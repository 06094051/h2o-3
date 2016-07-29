package water.fvec;

import hex.CreateFrame;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import water.Key;
import water.TestUtil;

import java.util.Arrays;

public class InteractionWrappedVecTest extends TestUtil {
  @BeforeClass static public void setup() {  stall_till_cloudsize(1); }

  @Test public void testIris() { // basic "can i construct the vec" test
    Frame fr=null;
    InteractionWrappedVec interactionVec=null;
    try {

      // interact species and sepal len -- all levels (expanded length is 3)
      fr = parse_test_file(Key.make("a.hex"), "smalldata/iris/iris_wheader.csv");
      interactionVec = new InteractionWrappedVec(fr.vecs().group().addVec(), fr.vecs().rowLayout(), null, null, true, true, false, fr.vecs(0,4));
      Assert.assertTrue(interactionVec.expandedLength()==3);
      interactionVec.remove();


      // interact species and sepal len -- not all factor levels
      interactionVec =  new InteractionWrappedVec(fr.vecs().group().addVec(), fr.vecs().rowLayout(), null, null, false,true, false, fr.vecs(0,4));
      Assert.assertTrue(interactionVec.expandedLength()==2); // dropped first level
      interactionVec.remove();

      // interact 2 numeric cols: sepal_len sepal_wid
      interactionVec =  new InteractionWrappedVec(fr.vecs().group().addVec(), fr.vecs().rowLayout(), null, null, true, true, false, fr.vecs(0,1));
      Assert.assertTrue(interactionVec.expandedLength()==1);
    } finally {
      if( fr!=null ) fr.delete();
      if( interactionVec!=null ) interactionVec.remove();
    }
  }

  // test interacting two enum columns
  @Test public void testTwoEnum() {
    Frame fr=null;
    InteractionWrappedVec interactionVec=null;
    int FAKEMAXFORTEST=1000;
    try {
      fr = parse_test_file(Key.make("a.hex"), "smalldata/airlines/allyears2k_headers.zip");
      interactionVec = new InteractionWrappedVec(fr.vecs().group().addVec(), fr.vecs().rowLayout(), null, null, true, true, false, fr.vecs(8,16));
      CreateInteractions.createInteractionDomain cid = new CreateInteractions.createInteractionDomain(false,false);
      cid.doAll(fr.vecs(8,16));

      // sorted according to occurence Greatest -> Least
      String[] domain = new CreateInteractions(FAKEMAXFORTEST,1).makeDomain(cid.getMap(), fr.vecs().domain(8), fr.vecs().domain(16));

      String modeDomain = domain[0];
      Arrays.sort(domain); // want to compare with interactionVec, so String sort them
      System.out.println(modeDomain);

      Assert.assertArrayEquals(interactionVec.domain(0), domain);

      Assert.assertTrue(interactionVec.expandedLength()==domain.length);
      interactionVec.remove();

      // don't include all cat levels
      interactionVec = new InteractionWrappedVec(fr.vecs().group().addVec(), fr.vecs().rowLayout(), null, null, false, true, false, fr.vecs(8,16));
      Assert.assertTrue(interactionVec.expandedLength()==286);

      System.out.println(interactionVec.mode(0));
      System.out.println(interactionVec.domain(0)[interactionVec.mode(0)]);
      System.out.println(Arrays.toString(interactionVec.getBins()));

      Assert.assertTrue(modeDomain.equals(interactionVec.domain(0)[interactionVec.mode(0)]));

    } finally {
      if( fr!=null ) fr.delete();
      if( interactionVec!=null ) interactionVec.remove();
    }
  }

  // test with enum restrictions
  @Test public void testEnumLimits() {
    Frame fr=null;
    InteractionWrappedVec interactionVec=null;

    int FAKEMAXFORTEST=1000;

    String[] A = new String[]{"US", "UA", "WN", "HP"};
    String[] B = new String[]{"PIT", "DEN"};
    try {
      fr = parse_test_file(Key.make("a.hex"), "smalldata/airlines/allyears2k_headers.zip");
      interactionVec = new InteractionWrappedVec(fr.vecs().group().addVec(), fr.vecs().rowLayout(), A, B, true, true, false, fr.vecs(8,16));

      int[] a = new int[A.length];
      int[] b = new int[B.length];
      int idx=0;
      for(String s:A) a[idx++]= Arrays.asList(fr.vecs().domain(8)).indexOf(s);
      idx=0;
      for(String s:B) b[idx++]= Arrays.asList(fr.vecs().domain(16)).indexOf(s);
      CreateInteractions.createInteractionDomain cid = new CreateInteractions.createInteractionDomain(false,false,a,b);
      cid.doAll(fr.vecs(8,16));
      String[] domain = new CreateInteractions(FAKEMAXFORTEST,1).makeDomain(cid.getMap(), fr.vecs().domain(8), fr.vecs().domain(16));
      Arrays.sort(domain);
      Assert.assertArrayEquals(interactionVec.domain(0), domain);
    } finally {
      if( fr!=null ) fr.delete();
      if( interactionVec!=null ) interactionVec.remove();
    }
  }


  Frame makeFrame(long rows) {
    CreateFrame cf = new CreateFrame();
    cf.rows = rows;
    cf.cols = 10;
    cf.categorical_fraction = 0.7;
    cf.integer_fraction = 0.1;
    cf.missing_fraction = 0.1;
    cf.binary_fraction = 0.1;
    cf.factors = 5;
    cf.response_factors = 2;
    cf.positive_response = false;
    cf.has_response = false;
    cf.seed = 1234;
    return cf.execImpl().get();
  }


  @Test public void testMultiChk1() {  //previous tests, but multichk
    Frame fr=null;
    InteractionWrappedVec interactionVec=null;
    try {

      fr = makeFrame(1<<20);
      interactionVec = new InteractionWrappedVec(fr.vecs().group().addVec(), fr.vecs().rowLayout(), null, null, true, true, false, fr.vecs(0,2));
      Assert.assertTrue(interactionVec.expandedLength()==5);
      interactionVec.remove();


      interactionVec =  new InteractionWrappedVec(fr.vecs().group().addVec(), fr.vecs().rowLayout(), null, null, false, true, false, fr.vecs(1,4));
      Assert.assertTrue(interactionVec.expandedLength()==4); // dropped first level
      interactionVec.remove();

      interactionVec =  new InteractionWrappedVec(fr.vecs().group().addVec(), fr.vecs().rowLayout(), null, null, true, true, false, fr.vecs(0,1));
      Assert.assertTrue(interactionVec.expandedLength()==1);
    } finally {
      if( fr!=null ) fr.delete();
      if( interactionVec!=null ) interactionVec.remove();
    }
  }

  @Test public void testMultiChk2() {
    Frame fr=null;
    InteractionWrappedVec interactionVec=null;
    int FAKEMAXFORTEST=1000;
    try {
      fr = makeFrame(1 << 20);
      interactionVec = new InteractionWrappedVec(fr.vecs().group().addVec(), fr.vecs().rowLayout(), null, null, true, true, false, fr.vecs(2,4));
      CreateInteractions.createInteractionDomain cid = new CreateInteractions.createInteractionDomain(false,false);
      cid.doAll(fr.vecs(2,4));

      // sorted according to occurence Greatest -> Least
      String[] domain = new CreateInteractions(FAKEMAXFORTEST,1).makeDomain(cid.getMap(), fr.vecs().domain(2), fr.vecs().domain(4));

      String modeDomain = domain[0];
      Arrays.sort(domain); // want to compare with interactionVec, so String sort them
      System.out.println(modeDomain);

      Assert.assertArrayEquals(interactionVec.domain(0), domain);

      Assert.assertTrue(interactionVec.expandedLength()==domain.length);
      interactionVec.remove();

      // don't include all cat levels
      interactionVec = new InteractionWrappedVec(fr.vecs().group().addVec(), fr.vecs().rowLayout(), null, null, false, true, false, fr.vecs(2,4));
      Assert.assertTrue(interactionVec.expandedLength()==16);

      System.out.println(interactionVec.mode(0));
      System.out.println(interactionVec.domain(interactionVec.mode(0)));
      System.out.println(Arrays.toString(interactionVec.getBins()));

      Assert.assertTrue(modeDomain.equals(interactionVec.domain(interactionVec.mode(0))));

    } finally {
      if( fr!=null ) fr.delete();
      if( interactionVec!=null ) interactionVec.remove();
    }
  }

  @Test public void testMultiChk3() {
    Frame fr=null;
    InteractionWrappedVec interactionVec=null;

    int FAKEMAXFORTEST=1000;

    String[] A;
    String[] B;
    try {
      fr = makeFrame(1 << 20);
      String[] fullA=fr.vecs().domain(3);
      String[] fullB=fr.vecs().domain(8);
      A = new String[]{fullA[0],fullA[3],fullA[4] };
      B = new String[]{fullB[1],fullB[0]};

      interactionVec = new InteractionWrappedVec(fr.vecs().group().addVec(), fr.vecs().rowLayout(), A, B, true, true, false, fr.vecs(3,8));

      int[] a = new int[A.length];
      int[] b = new int[B.length];
      int idx=0;
      for(String s:A) a[idx++]= Arrays.asList(fullA).indexOf(s);
      idx=0;
      for(String s:B) b[idx++]= Arrays.asList(fullB).indexOf(s);
      CreateInteractions.createInteractionDomain cid = new CreateInteractions.createInteractionDomain(false,false,a,b);
      cid.doAll(fr.vecs(3,8));
      String[] domain = new CreateInteractions(FAKEMAXFORTEST,1).makeDomain(cid.getMap(), fullA, fullB);
      Arrays.sort(domain);
      Assert.assertArrayEquals(interactionVec.domain(0), domain);
    } finally {
      if( fr!=null ) fr.delete();
      if( interactionVec!=null ) interactionVec.remove();
    }
  }
}
