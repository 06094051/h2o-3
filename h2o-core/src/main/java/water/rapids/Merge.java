package water.rapids;

import water.*;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.ArrayUtils;
import water.util.Log;
import water.util.MRUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Merge {

  // Hack to cleanup helpers made during merge
  // TODO: Keep radix order (Keys) around as hidden objects for a given frame and key column
  static void cleanUp() {
    new MRTask() {
      protected void setupLocal() {
        Object [] kvs = H2O.STORE.raw_array();
        for(int i = 2; i < kvs.length; i+= 2){
          Object ok = kvs[i];
          if( !(ok instanceof Key  ) ) continue; // Ignore tombstones and Primes and null's
          Key key = (Key )ok;
          if(!key.home())continue;
          String st = key.toString();
          if (st.contains("__radix_order__") || st.contains("__binary_merge__")) {
            DKV.remove(key);
          }
        }
      }
    }.doAllNodes();
  }

  // single-threaded driver logic
  static Frame merge(Frame leftFrame, Frame rightFrame, int leftCols[], int rightCols[], boolean allLeft) {

    // each of those launches an MRTask
    System.out.println("\nCreating left index ...");
    long t0 = System.nanoTime();
    RadixOrder leftIndex = new RadixOrder(leftFrame, leftCols);
    System.out.println("Creating left index took: " + (System.nanoTime() - t0) / 1e9);

    System.out.println("\nCreating right index ...");
    t0 = System.nanoTime();
    RadixOrder rightIndex = new RadixOrder(rightFrame, rightCols);
    System.out.println("Creating right index took: " + (System.nanoTime() - t0) / 1e9 + "\n");

    // Align MSB locations between the two keys
    // If the 1st join column has range < 256 (e.g. test cases) then <=8 bits are used and there's a floor of 8 to the shift.
    int bitShift = Math.max(8, rightIndex._biggestBit[0]) - Math.max(8, leftIndex._biggestBit[0]);
    int leftExtent = 256, rightExtent = 1;
    if (bitShift < 0) {
      // The biggest keys in left table are larger than the biggest in right table
      // Therefore those biggest ones don't have a match and we can instantly ignore them
      // The only msb's that can match are the smallest in left table ...
      leftExtent >>= -bitShift;
      // and those could join to multiple msb in the right table, one-to-many ...
      rightExtent <<= -bitShift;
    }
    // else if bitShift > 0
    //   The biggest keys in right table are larger than the largest in left table
    //   The msb values in left table need to be reduced in magnitude and will then join to the smallest of the right key's msb values
    //   Many left msb might join to the same (smaller) right msb
    //   Leave leftExtent at 256 and rightExtent at 1.
    //   The positive bitShift will reduce jbase below to common right msb's,  many-to-one
    // else bitShift == 0
    //   We hope most common case. Common width keys (e.g. ids, codes, enums, integers, etc) both sides over similar range
    //   Left msb will match exactly to right msb one-to-one, without any alignment needed.

    System.out.println("Sending BinaryMerge async RPC calls ...");
    t0 = System.nanoTime();
    List<RPC> bmList = new ArrayList<>();
    for (int leftMSB =0; leftMSB <leftExtent; leftMSB++) { // each of left msb values.  TO DO: go parallel
//      long leftLen = leftIndex._MSBhist[i];
//      if (leftLen > 0) {
      int rightMSBBase = leftMSB >> bitShift;  // could be positive or negative, or most commonly and ideally bitShift==0
      for (int k=0; k<rightExtent; k++) {
        int rightMSB = rightMSBBase +k;
//          long rightLen = rightIndex._MSBhist[j];
//          if (rightLen > 0) {
        //System.out.print(i + " left " + lenx + " => right " + leny);
        // TO DO: when go distributed, move the smaller of lenx and leny to the other one's node.
        //        if 256 are distributed across 10 nodes in order with 1-25 on node 1, 26-50 on node 2 etc, then most already will be on same node.
//        H2ONode leftNode = MoveByFirstByte.ownerOfMSB(leftMSB);
        H2ONode rightNode = MoveByFirstByte.ownerOfMSB(rightMSB);
        //if (leftMSB!=73 || rightMSB!=73) continue;
        //Log.info("Calling BinaryMerge for " + leftMSB + " " + rightMSB);
        RPC bm = new RPC<>(rightNode,
                new BinaryMerge(leftFrame, rightFrame,
                        leftMSB, rightMSB,
                        //leftNode.index(), //convention - right frame is local, but left frame is potentially remote
                        leftIndex._bytesUsed,   // field sizes for each column in the key
                        rightIndex._bytesUsed,
                        allLeft
                )
        );
        bmList.add(bm);
        bm.call(); //async
      }
    }
    System.out.println("Sending BinaryMerge async RPC calls took: " + (System.nanoTime() - t0) / 1e9);

    System.out.println("Summing BinaryMerge._numChunks (and waiting for RPCs to finish)... ");
    t0 = System.nanoTime();
    long ansN = 0;
    int numChunks = 0;
    BinaryMerge bmResults[] = new BinaryMerge[bmList.size()];
    int i=0;
    for (RPC rpc : bmList) {
      System.out.print(i + " ");  // seems like inserting this print fixes the pause.  // TODO: remove and see if it hangs again
      BinaryMerge thisbm;
      bmResults[i++] = thisbm = (BinaryMerge)rpc.get(); //block
      if (thisbm._numRowsInResult == 0) continue;
      numChunks += thisbm._chunkSizes.length;
      ansN += thisbm._numRowsInResult;
    }
    System.out.println("\ntook: " + (System.nanoTime() - t0) / 1e9);
    assert(i == bmList.size());

    System.out.print("Allocating and populating chunk info (e.g. size and batch number) ...");
    t0 = System.nanoTime();
    long chunkSizes[] = new long[numChunks];
    int chunkLeftMSB[] = new int[numChunks];  // using too much space repeating the same value here, but, limited
    int chunkRightMSB[] = new int[numChunks];
    int chunkBatch[] = new int[numChunks];
    int k = 0;
    for (i=0; i<bmList.size(); i++) {
      BinaryMerge thisbm = bmResults[i];
      if (thisbm._numRowsInResult == 0) continue;
      int thisChunkSizes[] = thisbm._chunkSizes;
      for (int j=0; j<thisChunkSizes.length; j++) {
        chunkSizes[k] = thisChunkSizes[j];
        chunkLeftMSB[k] = thisbm._leftMSB;
        chunkRightMSB[k] = thisbm._rightMSB;
        chunkBatch[k] = j;
        k++;
      }
    }
    System.out.println("took: " + (System.nanoTime() - t0) / 1e9);

    // Now we can stitch together the final frame from the raw chunks that were put into the store

    System.out.print("Allocating and populated espc ...");
    t0 = System.nanoTime();
    long espc[] = new long[chunkSizes.length+1];
    i=0;
    long sum=0;
    for (long s : chunkSizes) {
      espc[i++] = sum;
      sum+=s;
    }
    espc[espc.length-1] = sum;
    System.out.println("took: " + (System.nanoTime() - t0) / 1e9);
    assert(sum==ansN);

    System.out.print("Allocating dummy vecs/chunks of the final frame ...");
    t0 = System.nanoTime();
    int numJoinCols = leftIndex._bytesUsed.length;
    int numLeftCols = leftFrame.numCols();
    int numColsInResult = numLeftCols + rightFrame.numCols() - numJoinCols ;
    final byte[] types = new byte[numColsInResult];
    final String[][] doms = new String[numColsInResult][];
    final String[] names = new String[numColsInResult];
    for (int j=0; j<numLeftCols; j++) {
      types[j] = leftFrame.vec(j).get_type();
      doms[j] = leftFrame.domains()[j];
      names[j] = leftFrame.names()[j];
    }
    for (int j=0; j<rightFrame.numCols()-numJoinCols; j++) {
      types[numLeftCols + j] = rightFrame.vec(j+numJoinCols).get_type();
      doms[numLeftCols + j] = rightFrame.domains()[j+numJoinCols];
      names[numLeftCols + j] = rightFrame.names()[j+numJoinCols];
    }
    Key key = Vec.newKey();
    Vec[] vecs = new Vec(key, Vec.ESPC.rowLayout(key, espc)).makeCons(numColsInResult, 0, doms, types);
    // to delete ... String[] names = ArrayUtils.append(leftFrame.names(), ArrayUtils.select(rightFrame.names(),  ArrayUtils.seq(numJoinCols, rightFrame.numCols() - 1)));
    System.out.println("took: " + (System.nanoTime() - t0) / 1e9);

    System.out.print("Finally stitch together by overwriting dummies ...");
    t0 = System.nanoTime();
    Frame fr = new Frame(Key.make(rightFrame._key.toString() + "_joined_with_" + leftFrame._key.toString()), names, vecs);
    ChunkStitcher ff = new ChunkStitcher(leftFrame, rightFrame, chunkSizes, chunkLeftMSB, chunkRightMSB, chunkBatch);
    ff.doAll(fr);
    System.out.println("took: " + (System.nanoTime() - t0) / 1e9);

    Merge.cleanUp();
    return fr;
  }

  static class ChunkStitcher extends MRTask<ChunkStitcher> {
    final Frame _leftFrame;
    final Frame _rightFrame;
    final long _chunkSizes[];
    final int _chunkLeftMSB[];
    final int _chunkRightMSB[];
    final int _chunkBatch[];
    public ChunkStitcher(Frame leftFrame,
                         Frame rightFrame,
                         long[] chunkSizes,
                         int[] chunkLeftMSB,
                         int[] chunkRightMSB,
                         int[] chunkBatch
    ) {
      _leftFrame = leftFrame;
      _rightFrame = rightFrame;
      _chunkSizes = chunkSizes;
      _chunkLeftMSB = chunkLeftMSB;
      _chunkRightMSB = chunkRightMSB;
      _chunkBatch = chunkBatch;
    }
    @Override
    public void map(Chunk[] cs) {
      int chkIdx = cs[0].cidx();
      Futures fs = new Futures();
      for (int i=0;i<cs.length;++i) {
        Key destKey = cs[i].vec().chunkKey(chkIdx);
        assert(cs[i].len() == _chunkSizes[chkIdx]);
        Key k = BinaryMerge.getKeyForMSBComboPerCol(_leftFrame, _rightFrame, _chunkLeftMSB[chkIdx], _chunkRightMSB[chkIdx], i, _chunkBatch[chkIdx]);
        Chunk ck = DKV.getGet(k);
        DKV.put(destKey, ck, fs);
        DKV.remove(k);
      }
      fs.blockForPending();
    }
  }
}

