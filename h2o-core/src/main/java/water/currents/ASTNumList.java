package water.currents;

import water.util.SB;
import water.H2O;
import java.util.ArrayList;

/** A collection of base/stride/cnts.  Bases are monotonically increasing, and
 *  base+stride*cnt is always less than the next base.  This is a syntatic form
 *  only, and never executes and never gets on the execution stack.
 */
class ASTNumList extends AST {
  final double _bases[], _strides[];
  final long _cnts[];
  ASTNumList( Exec e ) {
    ArrayList<Double> bases  = new ArrayList<>();
    ArrayList<Double> strides= new ArrayList<>();
    ArrayList<Long>   cnts   = new ArrayList<>();
    e.xpeek('[');
    double last = -Double.MAX_VALUE;

    // Parse a number list
    while( true ) {
      char c = e.skipWS();
      if( c==']' ) break;
      if( c=='#' ) { e._x++; c = e.peek(); }
      double base = e.number(), cnt=1, stride=1;
      c = e.skipWS();
      if( c==':' ) {
        e.xpeek(':'); e.skipWS();
        cnt = e.number();
        if( cnt < 1 || ((long)cnt) != cnt )
          throw new IllegalArgumentException("Count must be a integer larger than zero, "+cnt);
        c = e.skipWS();
        if( c==':' ) {
          e.xpeek(':'); e.skipWS();
          stride = e.number();
          if( stride < 0 )
            throw new IllegalArgumentException("Stride must be positive, "+stride);
          c = e.skipWS();
        }
      }
      if( base < last )
        throw new IllegalArgumentException("Number lists must always increase, but "+last+" is not less than "+base);
      last = base+(cnt-1)*stride; // last max value
      bases.add(base);  
      cnts.add((long)cnt);  
      strides.add(stride);
      // Optional comma seperating span
      if( c==',') e.xpeek(',');
    }
    e.xpeek(']');

    _bases  = new double[bases.size()];
    _strides= new double[bases.size()];
    _cnts   = new long  [bases.size()];
    for( int i=0; i<_bases.length; i++ ) {
      _bases  [i] = bases  .get(i);
      _cnts   [i] = cnts   .get(i);
      _strides[i] = strides.get(i);
    }
  }

  // This is a special syntatic form; the number-list never executes and hits
  // the execution stack
  @Override Val exec( Env env ) { throw H2O.fail(); }

  @Override public String str() { 
    SB sb = new SB().p('[');
    for( int i=0; i<_bases.length; i++ ) {
      sb.p(_bases[i]);
      if( _cnts[i] != 1 ) {
        sb.p(':').p(_bases[i]+_cnts[i]*_strides[i]);
        if( _strides[i] != 1 || ((long)_bases[i])!=_bases[i] )
          sb.p(':').p(_strides[i]);
      }
      if( i < _bases.length-1 ) sb.p(',');
    }
    return sb.p(']').toString();
  }
  // Strange count of args, due to custom parsing
  @Override int nargs() { return -1; }

  // Expand the compressed form into an array of doubles
  double[] expand() {
    // Count total values
    int nrows=0, r=0;
    for( int i=0; i<_bases.length; i++ ) nrows += _cnts[i];
    // Fill in values
    double[] vals = new double[nrows];
    for( int i=0; i<_bases.length; i++ )
      for( double d = _bases[i]; d<_bases[i]+_cnts[i]*_strides[i]; d+=_strides[i] )
        vals[r++] = d;
    return vals;
  }

  double max() { return _bases[_bases.length-1] + _cnts[_cnts.length-1] - 1; } // largest inclusive value
  double min() { return _bases[0]; }

  // check if n is in this list of numbers
  // NB: all contiguous ranges have already been checked to have stride 1
  boolean has(long v) {
    if( min() <= v && v <= max() ) {
      // binary search _bases for range to check, return true for exact match
      // if no exact base matches, check the ranges of the two "bounding" bases
      int[][] res = new int[2][]; // entry 0 is exact; entry 1 is [lb,ub]
      bsearch(v, res);
      if( res[0] != null /* exact base match */ ) return true;
      else {
        int lb = res[1][0], ub = res[1][1];
        if( _bases[lb] <= v && v < _bases[lb] + _cnts[lb] ) return true;
        if( _bases[ub] <= v && v < _bases[ub] + _cnts[ub] ) return true;
      }
    }
    return false;
  }

  private void bsearch(long v, int[][] res) {
    int lb=0,ub=_bases.length;
    int m=(ub+lb)>>1; // [lb,m) U [m,ub)
    do {
      if( v==_bases[m] ) { res[0]=new int[]{m}; return; } // exact base match
      else if( v<_bases[m] ) ub=m;
      else lb = m;
      m = (ub+lb)>>1;
    } while( m!=lb );
    res[1]=new int[]{lb,ub}; // return 2 closest bases
  }
}