package hex.glm;

import hex.DataInfo;
import hex.glm.GLMTask.GLMIterationTask;
import hex.glm.GLMTask.GLMGradientTask;
import hex.glm.GLMTask.GLMLineSearchTask;
import org.junit.BeforeClass;
import org.junit.Test;

import hex.glm.GLMModel.GLMParameters;
import hex.glm.GLMModel.GLMParameters.Family;
import hex.glm.GLMModel.GetScoringModelTask;
import hex.glm.GLMModel.Submodel;
import hex.utils.MSETsk;
import water.*;
import water.fvec.FVecTest;
import water.fvec.Frame;
import water.fvec.Vec;
import water.parser.ParseDataset;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Random;
import java.util.concurrent.ExecutionException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class GLMTest  extends TestUtil {
  @BeforeClass public static void setup() { stall_till_cloudsize(1); }

  //------------------- simple tests on synthetic data------------------------------------
  @Test
  public void testGaussianRegression() throws InterruptedException, ExecutionException {
    GLM job = null;
    Key raw = Key.make("gaussian_test_data_raw");
    Key parsed = Key.make("gaussian_test_data_parsed");
    Key modelKey = Key.make("gaussian_test");
    GLMModel model = null;
    Frame fr = null;
    try {
      // make data so that the expected coefficients is icept = col[0] = 1.0
      FVecTest.makeByteVec(raw, "x,y\n0,0\n1,0.1\n2,0.2\n3,0.3\n4,0.4\n5,0.5\n6,0.6\n7,0.7\n8,0.8\n9,0.9");
      fr = ParseDataset.parse(parsed, raw);
      GLMParameters params = new GLMParameters(Family.gaussian);
      params._train = fr._key;
      // params._response = 1;
      params._response_column = fr._names[1];
      params._lambda = new double[]{0};
      job = new GLM(modelKey,"glm test simple gaussian",params);
      job.trainModel().get();
      model = DKV.get(modelKey).get();
      HashMap<String, Double> coefs = model.coefficients();
      System.out.println("coefs = " + coefs);
      assertEquals(0.0,coefs.get("Intercept"),1e-4);
      assertEquals(0.1,coefs.get("x"),1e-4);
    }finally{
      if( fr != null ) fr.remove();
      if(model != null)model.remove();
      if( job != null ) job.remove();
    }
  }


  /**
   * Test Poisson regression on simple and small synthetic dataset.
   * Equation is: y = exp(x+1);
   */
  @Test public void testPoissonRegression() throws InterruptedException, ExecutionException {
    GLM job = null;
    Key raw = Key.make("poisson_test_data_raw");
    Key parsed = Key.make("poisson_test_data_parsed");
    Key modelKey = Key.make("poisson_test");
    GLMModel model = null;
    Frame fr = null;
    try {
      // make data so that the expected coefficients is icept = col[0] = 1.0
      FVecTest.makeByteVec(raw, "x,y\n0,2\n1,4\n2,8\n3,16\n4,32\n5,64\n6,128\n7,256");
      fr = ParseDataset.parse(parsed, raw);
      Vec v = fr.vec(0);
      System.out.println(v.min() + ", " + v.max()  + ", mean = " + v.mean());
      GLMParameters params = new GLMParameters(Family.poisson);
      params._train = fr._key;
      // params._response = 1;
      params._response_column = fr._names[1];
      params._lambda = new double[]{0};
      params._standardize = false;
      job = new GLM(modelKey,"glm test simple poisson",params);
      job.trainModel().get();
      model = DKV.get(modelKey).get();
      for(double c:model.beta())assertEquals(Math.log(2),c,1e-2); // only 1e-2 precision cause the perfect solution is too perfect -> will trigger grid search
      model.delete();
      fr.delete();
      job.remove();

      // Test 2, example from http://www.biostat.umn.edu/~dipankar/bmtry711.11/lecture_13.pdf
      FVecTest.makeByteVec(raw, "x,y\n1,0\n2,1\n3,2\n4,3\n5,1\n6,4\n7,9\n8,18\n9,23\n10,31\n11,20\n12,25\n13,37\n14,45\n");
      fr = ParseDataset.parse(parsed, raw);
      GLMParameters params2 = new GLMParameters(Family.poisson);
      params2._train = fr._key;
      // params2._response = 1;
      params2._response_column = fr._names[1];
      params2._lambda = new double[]{0};
      params2._standardize = false;
      params2._beta_epsilon = 1e-5;
      job = new GLM(modelKey,"glm test simple poisson",params2);
      model = job.trainModel().get();
      assertEquals(0.3396,model.beta()[1],1e-4);
      assertEquals(0.2565,model.beta()[0],1e-4);
      // test scoring

    } finally {
      if( fr != null ) fr.delete();
      if(model != null)model.delete();
      if( job != null ) job.remove();
    }
  }


  /**
   * Test Gamma regression on simple and small synthetic dataset.
   * Equation is: y = 1/(x+1);
   * @throws ExecutionException
   * @throws InterruptedException
   */
  @Test public void testGammaRegression() throws InterruptedException, ExecutionException {
    GLM job = null;
    GLMModel model = null;
    Frame fr = null;
    try {
      // make data so that the expected coefficients is icept = col[0] = 1.0
      Key raw = Key.make("gamma_test_data_raw");
      Key parsed = Key.make("gamma_test_data_parsed");
      FVecTest.makeByteVec(raw, "x,y\n0,1\n1,0.5\n2,0.3333333\n3,0.25\n4,0.2\n5,0.1666667\n6,0.1428571\n7,0.125");
      fr = ParseDataset.parse(parsed, raw);
//      /public GLM2(String desc, Key dest, Frame src, Family family, Link link, double alpha, double lambda) {
//      double [] vals = new double[] {1.0,1.0};
      //public GLM2(String desc, Key dest, Frame src, Family family, Link link, double alpha, double lambda) {
      GLMParameters params = new GLMParameters(Family.gamma);
      // params._response = 1;
      params._response_column = fr._names[1];
      params._train = parsed;
      params._lambda = new double[]{0};
      Key modelKey = Key.make("gamma_test");
      job = new GLM(modelKey,"glm test simple gamma",params);
      job.trainModel().get();
      model = DKV.get(modelKey).get();
      for(double c:model.beta())assertEquals(1.0, c,1e-4);
      // test scoring
    }finally{
      if( fr != null ) fr.delete();
      if(model != null)model.delete();
      if( job != null ) job.remove();
    }
  }

////  //simple tweedie test
//  @Test public void testTweedieRegression() throws InterruptedException, ExecutionException{
//    Key raw = Key.make("gaussian_test_data_raw");
//    Key parsed = Key.make("gaussian_test_data_parsed");
//    Key modelKey = Key.make("gaussian_test");
//    Frame fr = null;
//    GLMModel model = null;
//    try {
//      // make data so that the expected coefficients is icept = col[0] = 1.0
//      FVecTest.makeByteVec(raw, "x,y\n0,0\n1,0.1\n2,0.2\n3,0.3\n4,0.4\n5,0.5\n6,0.6\n7,0.7\n8,0.8\n9,0.9\n0,0\n1,0\n2,0\n3,0\n4,0\n5,0\n6,0\n7,0\n8,0\n9,0");
//      fr = ParseDataset.parse(parsed, new Key[]{raw});
//      double [] powers = new double [] {1.5,1.1,1.9};
//      double [] intercepts = new double []{3.643,1.318,9.154};
//      double [] xs = new double []{-0.260,-0.0284,-0.853};
//      for(int i = 0; i < powers.length; ++i){
//        DataInfo dinfo = new DataInfo(fr, 1, false, DataInfo.TransformType.NONE);
//        GLMParameters glm = new GLMParameters(Family.tweedie);
//
//        new GLM2("GLM test of gaussian(linear) regression.",Key.make(),modelKey,dinfo,glm,new double[]{0},0).fork().get();
//        model = DKV.get(modelKey).get();
//        testHTML(model);
//        HashMap<String, Double> coefs = model.coefficients();
//        assertEquals(intercepts[i],coefs.get("Intercept"),1e-3);
//        assertEquals(xs[i],coefs.get("x"),1e-3);
//      }
//    }finally{
//      if( fr != null ) fr.delete();
//      if(model != null)model.delete();
//    }
//  }

  static int rank(double [] ary) {
    int res = 0;
    for(int i = 0; i < ary.length-1; ++i)
      if(ary[i] != 0) ++res;
    return res;

  }

  @Test public void testLineSearchTask () {
    GLM job = null;
    Key parsed = Key.make("cars_parsed");
    Key modelKey = Key.make("cars_model");
    Frame fr = null;
    GLMModel model = null;
    Frame score = null;
    DataInfo dinfo = null;
    double ymu = 0;
    try {
      fr = parse_test_file(parsed, "smalldata/junit/mixcat_train.csv");
      GLMParameters params = new GLMParameters(Family.binomial, Family.binomial.defaultLink, new double[]{0}, new double[]{0});
      // params._response = fr.find(params._response_column);
      params._train = parsed;
      params._lambda = new double[]{0};
      params._use_all_factor_levels = true;
      fr.add("Useless", fr.remove("Useless"));

      dinfo = new DataInfo(Key.make(), fr, null, 1, params._use_all_factor_levels || params._lambda_search, params._standardize ? DataInfo.TransformType.STANDARDIZE : DataInfo.TransformType.NONE, DataInfo.TransformType.NONE, true, 0 /* no row weights */);
      DKV.put(dinfo._key, dinfo);

      double [] beta = MemoryManager.malloc8d(dinfo.fullN()+1);
      double [] pk   = MemoryManager.malloc8d(dinfo.fullN()+1);
      Random rnd = new Random(987654321);
      for(int i = 0; i < beta.length; ++i) {
        beta[i] = 1 - 2 * rnd.nextDouble();
        pk[i]   = 10* (1 - 2*rnd.nextDouble());
      }
      GLMLineSearchTask glst = new GLMLineSearchTask(dinfo,0,0,params, 1, beta,pk,.7,16).doAll(dinfo._adaptedFrame);
      double step = 1, stepDec = .7;
      for(int i = 0; i < glst._nSteps; ++i) {
        double [] b =  beta.clone();
        for(int j = 0; j < b.length; ++j) {
          b[j] += step * pk[j];
        }
        GLMIterationTask glmt = new GLMTask.GLMIterationTask(null, dinfo, 0, params, true, b, ymu, null).doAll(dinfo._adaptedFrame);
        assertEquals("objective values differ at step " + i + ": " + step, glmt._likelihood, glst._likelihoods[i], 1e-8);
        System.out.println("step = " + step + ", obj = " + glmt._likelihood + ", " + glst._likelihoods[i]);
        step *= stepDec;
      }
    } finally {
      if (fr != null) fr.delete();
      if (score != null) score.delete();
      if (model != null) model.delete();
      if (job != null) job.remove();
      if (dinfo != null) dinfo.remove();
    }
  }
  // Make sure all three implementations of gradient computation in GLM get the same results
  @Test public void testGradientTask(){
    GLM job = null;
    Key parsed = Key.make("cars_parsed");
    Key modelKey = Key.make("cars_model");
    Frame fr = null;
    GLMModel model = null;
    Frame score = null;
    DataInfo dinfo = null;
    try {
      fr = parse_test_file(parsed, "smalldata/junit/mixcat_train.csv");
      GLMParameters params = new GLMParameters(Family.binomial, Family.binomial.defaultLink, new double[]{0}, new double[]{0});
      // params._response = fr.find(params._response_column);
      params._train = parsed;
      params._lambda = new double[]{0};
      params._use_all_factor_levels = true;
      fr.add("Useless",fr.remove("Useless"));

      dinfo = new DataInfo(Key.make(), fr, null, 1, params._use_all_factor_levels || params._lambda_search, params._standardize ? DataInfo.TransformType.STANDARDIZE : DataInfo.TransformType.NONE, DataInfo.TransformType.NONE, true, 0 /* no row weights */);
      DKV.put(dinfo._key,dinfo);
      double ymu = 0;
      double [] beta = MemoryManager.malloc8d(dinfo.fullN()+1);
      Random rnd = new Random(987654321);
      for(int i = 0; i < beta.length; ++i)
      beta[i] = 1 - 2*rnd.nextDouble();

      GLMGradientTask grtCol = new GLMGradientTask(dinfo,params,params._lambda[0],beta,1).forceColAccess().doAll(dinfo._adaptedFrame);
      GLMGradientTask grtRow = new GLMGradientTask(dinfo,params,params._lambda[0],beta,1).forceRowAccess().doAll(dinfo._adaptedFrame);
      for(int i = 0; i < beta.length; ++i)
        assertEquals("gradients differ", grtRow._gradient[i], grtCol._gradient[i], 1e-4);
      params = new GLMParameters(Family.gaussian, Family.gaussian.defaultLink, new double[]{0}, new double[]{0});
      params._use_all_factor_levels = false;
      dinfo.remove();
      dinfo = new DataInfo(Key.make(), fr, null, 1, params._use_all_factor_levels || params._lambda_search, params._standardize ? DataInfo.TransformType.STANDARDIZE : DataInfo.TransformType.NONE, DataInfo.TransformType.NONE, true, 0 /* no row weights */);
      DKV.put(dinfo._key,dinfo);
      beta = MemoryManager.malloc8d(dinfo.fullN()+1);
      rnd = new Random(1987654321);
      for(int i = 0; i < beta.length; ++i)
        beta[i] = 1 - 2*rnd.nextDouble();
      grtCol = new GLMGradientTask(dinfo, params,params._lambda[0], beta, 1).forceColAccess().doAll(dinfo._adaptedFrame);
      grtRow = new GLMGradientTask(dinfo, params,params._lambda[0], beta, 1).forceRowAccess().doAll(dinfo._adaptedFrame);

      for(int i = 0; i < beta.length; ++i)
        assertEquals("gradients differ: " + Arrays.toString(grtRow._gradient) + " != " + Arrays.toString(grtCol._gradient), grtRow._gradient[i], grtCol._gradient[i], 1e-4);
      dinfo.remove();
      fr = parse_test_file(parsed, "smalldata/junit/cars.csv");
      params = new GLMParameters(Family.poisson, Family.poisson.defaultLink, new double[]{0}, new double[]{0});
      // params._response = fr.find(params._response_column);
      params._train = parsed;
      params._lambda = new double[]{0};
      params._use_all_factor_levels = true;
      dinfo = new DataInfo(Key.make(), fr, null, 1, params._use_all_factor_levels || params._lambda_search, params._standardize ? DataInfo.TransformType.STANDARDIZE : DataInfo.TransformType.NONE, DataInfo.TransformType.NONE, true, 0 /* no row weights */);
      DKV.put(dinfo._key,dinfo);
      ymu = 0;
      beta = MemoryManager.malloc8d(dinfo.fullN()+1);
      rnd = new Random(987654321);
      for(int i = 0; i < beta.length; ++i)
        beta[i] = 1 - 2*rnd.nextDouble();

      grtCol = new GLMGradientTask(dinfo,params,params._lambda[0],beta,1).forceColAccess().doAll(dinfo._adaptedFrame);
      grtRow = new GLMGradientTask(dinfo,params,params._lambda[0],beta,1).forceRowAccess().doAll(dinfo._adaptedFrame);
      for(int i = 0; i < beta.length; ++i)
        assertEquals("gradients differ: " + Arrays.toString(grtRow._gradient) + " != " + Arrays.toString(grtCol._gradient), grtRow._gradient[i], grtCol._gradient[i], 1e-4);
      dinfo.remove();
      // arcene takes too long
//      fr = parse_test_file(parsed, "smalldata/glm_test/arcene.csv");
//      params = new GLMParameters(Family.gaussian, Family.gaussian.defaultLink, new double[]{0}, new double[]{0});
//      // params._response = fr.find(params._response_column);
//      params._train = parsed;
//      params._lambda = new double[]{0};
//      params._use_all_factor_levels = true;
//      dinfo = new DataInfo(Key.make(), fr, null, 1, params._use_all_factor_levels || params._lambda_search, params._standardize ? DataInfo.TransformType.STANDARDIZE : DataInfo.TransformType.NONE, DataInfo.TransformType.NONE, true);
//      DKV.put(dinfo._key,dinfo);
//      ymu = 0;
//      beta = MemoryManager.malloc8d(dinfo.fullN()+1);
//      rnd = new Random(987654321);
//      for(int i = 0; i < beta.length; ++i)
//        beta[i] = 1 - 2*rnd.nextDouble();
//      glmt = new GLMTask.GLMIterationTask(null,dinfo,params,false,true,true,beta,ymu,1, ModelUtils.DEFAULT_THRESHOLDS,null).doAll(dinfo._adaptedFrame);
//      grtCol = new GLMGradientTask(dinfo,params,params._lambda[0],beta,1).forceColAccess().doAll(dinfo._adaptedFrame);
//      grtRow = new GLMGradientTask(dinfo,params,params._lambda[0],beta,1).forceRowAccess().doAll(dinfo._adaptedFrame);
//      for(int i = 0; i < beta.length; ++i) {
//        assertEquals("gradients differ", grtRow._gradient[i], grtCol._gradient[i], 1e-4);
//        assertEquals("gradients differ", glmt._ginfo._gradient[i], grtRow._gradient[i], 1e-4);
//      }
    } finally {
      if( fr != null ) fr.delete();
      if(score != null)score.delete();
      if(model != null)model.delete();
      if( job != null ) job.remove();
      if(dinfo != null) dinfo.remove();
    }
  }
  //------------ TEST on selected files form small data and compare to R results ------------------------------------
  /**
   * Simple test for poisson, gamma and gaussian families (no regularization, test both lsm solvers).
   * Basically tries to predict horse power based on other parameters of the cars in the dataset.
   * Compare against the results from standard R glm implementation.
   * @throws ExecutionException
   * @throws InterruptedException
   */
  @Test public void testCars() throws InterruptedException, ExecutionException{
    GLM job = null;
    Key parsed = Key.make("cars_parsed");
    Key modelKey = Key.make("cars_model");
    Frame fr = null;
    GLMModel model = null;
    Frame score = null;
    try {
      fr = parse_test_file(parsed, "smalldata/junit/cars.csv");
      GLMParameters params = new GLMParameters(Family.poisson, Family.poisson.defaultLink, new double[]{0}, new double[]{0});
      params._response_column = "power (hp)";
      // params._response = fr.find(params._response_column);
      params._ignored_columns = new String[]{"name"};
      params._train = parsed;
      params._lambda = new double[]{0};
      params._alpha = new double[]{0};
      job = new GLM(modelKey, "glm test simple poisson", params);
      job.trainModel().get();
      model = DKV.get(modelKey).get();
      HashMap<String, Double> coefs = model.coefficients();
      String[] cfs1 = new String[]{"Intercept", "economy (mpg)", "cylinders", "displacement (cc)", "weight (lb)", "0-60 mph (s)", "year"};
      double[] vls1 = new double[]{4.9504805, -0.0095859, -0.0063046, 0.0004392, 0.0001762, -0.0469810, 0.0002891};
      for (int i = 0; i < cfs1.length; ++i)
        assertEquals(vls1[i], coefs.get(cfs1[i]), 1e-4);
      // test gamma
      double[] vls2 = new double[]{8.992e-03, 1.818e-04, -1.125e-04, 1.505e-06, -1.284e-06, 4.510e-04, -7.254e-05};
      score = model.score(fr);
      GLMValidation val = model.validation();
//      GLMValidation val2 = new GLMValidationTsk(params,model._ymu,rank(model.beta())).doAll(new Vec[]{fr.vec("power (hp)"),score.vec("predict")})._val;
//      assertEquals(val.residualDeviance(),val2.residualDeviance(),1e-5);
//      assertEquals(val.nullDeviance(),val2.nullDeviance(),1e-6);
      score.delete();
      model.delete();
      job.remove();

      params = new GLMParameters(Family.gamma, Family.gamma.defaultLink, new double[]{0}, new double[]{0});
      params._response_column = "power (hp)";
      // params._response = fr.find(params._response_column);
      params._ignored_columns = new String[]{"name"};
      params._train = parsed;
      params._lambda = new double[]{0};
      params._beta_epsilon = 1e-5;
      job = new GLM(modelKey, "glm test simple poisson", params);
      job.trainModel().get();
      model = DKV.get(modelKey).get();
      coefs = model.coefficients();
      for (int i = 0; i < cfs1.length; ++i)
        assertEquals(vls2[i], coefs.get(cfs1[i]), 1e-4);
      score = model.score(fr);
      val = model.validation();
//      val2 = new GLMValidationTsk(params,model._ymu,rank(model.beta())).doAll(new Vec[]{fr.vec("power (hp)"),score.vec("predict")})._val;
//      assertEquals(val.residualDeviance(),val2.residualDeviance(),1e-6);
//      assertEquals(val.nullDeviance(),val2.nullDeviance(),1e-6);
      model.delete();
      job.remove();

      // test gaussian
      double[] vls3 = new double[]{166.95862, -0.00531, -2.46690, 0.12635, 0.02159, -4.66995, -0.85724};
      params = new GLMParameters(Family.gaussian);
      params._response_column = "power (hp)";
      // params._response = fr.find(params._response_column);
      params._ignored_columns = new String[]{"name"};
      params._train = parsed;
      params._lambda = new double[]{0};
      job = new GLM(modelKey, "glm test simple poisson", params);
      job.trainModel().get();
      model = DKV.get(modelKey).get();
      coefs = model.coefficients();
      for (int i = 0; i < cfs1.length; ++i)
        assertEquals(vls3[i], coefs.get(cfs1[i]), 1e-4);
      // test scoring
    } finally {
      if( fr != null ) fr.delete();
      if(score != null)score.delete();
      if(model != null)model.delete();
      if( job != null ) job.remove();
    }
  }

  // Leask xval keys
//  @Test public void testXval() {
//    GLM job = null;
//    GLMModel model = null;
//    Frame fr = parse_test_file("smalldata/glm_test/prostate_cat_replaced.csv");
//    Frame score = null;
//    try{
//      Scope.enter();
//      // R results
////      Coefficients:
////        (Intercept)           ID          AGE       RACER2       RACER3        DPROS        DCAPS          PSA          VOL      GLEASON
////          -8.894088     0.001588    -0.009589     0.231777    -0.459937     0.556231     0.556395     0.027854    -0.011355     1.010179
//      String [] cfs1 = new String [] {"Intercept","AGE", "RACE.R2","RACE.R3", "DPROS", "DCAPS", "PSA", "VOL", "GLEASON"};
//      double [] vals = new double [] {-8.14867, -0.01368, 0.32337, -0.38028, 0.55964, 0.49548, 0.02794, -0.01104, 0.97704};
//      GLMParameters params = new GLMParameters(Family.binomial);
//      params._n_folds = 10;
//      params._response_column = "CAPSULE";
//      params._ignored_columns = new String[]{"ID"};
//      params._train = fr._key;
//      params._lambda = new double[]{0};
//      job = new GLM(Key.make("prostate_model"),"glm test simple poisson",params);
//      model = job.trainModel().get();
//      HashMap<String, Double> coefs = model.coefficients();
//      for(int i = 0; i < cfs1.length; ++i)
//        assertEquals(vals[i], coefs.get(cfs1[i]),1e-4);
//      GLMValidation val = model.validation();
////      assertEquals(512.3, val.nullDeviance(),1e-1);
////      assertEquals(378.3, val.residualDeviance(),1e-1);
////      assertEquals(396.3, val.aic(),1e-1);
////      score = model.score(fr);
////
////      hex.ModelMetrics mm = hex.ModelMetrics.getFromDKV(model,fr);
////
////      AUCData adata = mm._aucdata;
////      assertEquals(val.auc(),adata.AUC(),1e-2);
////      GLMValidation val2 = new GLMValidationTsk(params,model._ymu,rank(model.beta())).doAll(new Vec[]{fr.vec("CAPSULE"),score.vec("1")})._val;
////      assertEquals(val.residualDeviance(),val2.residualDeviance(),1e-6);
////      assertEquals(val.nullDeviance(),val2.nullDeviance(),1e-6);
//    } finally {
//      fr.delete();
//      if(model != null)model.delete();
//      if(score != null)score.delete();
//      if( job != null ) job.remove();
//      Scope.exit();
//    }
//  }


//  @Test public void testProximal() {
//    glmnet's result:
//    res2 <- glmnet(x=M,y=D$CAPSULE,lower.limits=-.5,upper.limits=.5,family='binomial')
//    res2$beta[,58]
//    AGE        RACE          DPROS       PSA         VOL         GLEASON
//    -0.00616326 -0.50000000  0.50000000  0.03628192 -0.01249324  0.50000000 //    res2$a0[100]
//    res2$a0[58]
//    s57
//    -4.155864
//    lambda = 0.001108, null dev =  512.2888, res dev = 379.7597
//    Key parsed = Key.make("prostate_parsed");
//    Key modelKey = Key.make("prostate_model");
//    GLMModel model = null;
//    Frame fr = parse_test_file(parsed, "smalldata/logreg/prostate.csv", new String[]{"ID"}, "CAPSULE");
//    Key k = Key.make("rebalanced");
//    H2O.submitTask(new RebalanceDataSet(fr, k, 64)).join();
//    fr.delete();
//    fr = DKV.get(k).get();
//    fr.remove("ID");
//    Key betaConsKey = Key.make("beta_constraints");
//
//    //String[] cfs1 = new String[]{"RACE", "AGE", "DPROS", "DCAPS", "PSA", "VOL", "GLEASON","Intercept"};
//    //double[] vals = new double[]{0, 0, 0.54788332,0.53816534, 0.02380097, 0, 0.98115670,-8.945984};
//    // [AGE, RACE, DPROS, DCAPS, PSA, VOL, GLEASON, Intercept]
//    FVecTest.makeByteVec(betaConsKey, "names, beta_given, rho\n AGE, 0.1, 1\n RACE, -0.1, 1 \n DPROS, 10, 1 \n DCAPS, -10, 1 \n PSA, 0, 1\n VOL, 0, 1\nGLEASON, 0, 1\n Intercept, 0, 0 \n");
//    Frame betaConstraints = ParseDataset2.parse(parsed, new Key[]{betaConsKey});
//    try {
//      // H2O differs on intercept and race, same residual deviance though
//      GLM2.Source src = new GLM2.Source((Frame)fr.clone(), fr.vec("CAPSULE"), false, true);
//      new GLM2("GLM offset test on prostate.", Key.make(), modelKey, src, Family.binomial).setNonNegative(false).setRegularization(new double[]{0},new double[]{0.000}).setBetaConstraints(betaConstraints).setHighAccuracy().doInit().fork().get(); //.setHighAccuracy().doInit().fork().get();
//      model = DKV.get(modelKey).get();
//      fr.add("CAPSULE", fr.remove("CAPSULE"));
//      DataInfo dinfo = new DataInfo(fr, 1, true, false, TransformType.NONE, DataInfo.TransformType.NONE);
//      GLMIterationTask glmt = new GLMTask.GLMIterationTask(0,null, dinfo, new GLMParams(Family.binomial),false, true, true, model.beta(), 0, 1.0/380, ModelUtils.DEFAULT_THRESHOLDS, null).doAll(dinfo._adaptedFrame);
//      double [] beta = model.beta();
//      double [] grad = glmt.gradient(0,0);
//      for(int i = 0; i < beta.length; ++i)
//        Assert.assertEquals(0, grad[i] + betaConstraints.vec("rho").at(i) * (beta[i] - betaConstraints.vec("beta_given").at(i)), 1e-8);
//      // now standardized
//    } finally {
//      fr.delete();
//      if(model != null)model.delete();
//    }
//  }
  // test categorical autoexpansions, run on airlines which has several categorical columns,
  // once on explicitly expanded data, once on h2o autoexpanded and compare the results
  @Test public void testAirlines() {
    GLM job = null;
    GLMModel model1 = null, model2 = null, model3 = null, model4 = null;
    Frame frMM = parse_test_file("smalldata/airlines/AirlinesTrainMM.csv.zip");
    frMM.remove("").remove();
    DKV.put(frMM._key,frMM);
    Frame fr = parse_test_file("smalldata/airlines/AirlinesTrain.csv.zip");

    //  Distance + Origin + Dest + UniqueCarrier
    String [] ignoredCols = new String[]{"fYear", "fMonth", "fDayofMonth", "fDayOfWeek", "DepTime","ArrTime","IsDepDelayed_REC"};
    try{
      Scope.enter();
      GLMParameters params = new GLMParameters(Family.gaussian);
      params._response_column = "IsDepDelayed";
      params._ignored_columns = ignoredCols;
      params._train = fr._key;
      params._lambda = new double[]{1e-5};
//      params._alpha = new double[]{0};
      params._standardize = false;
      job = new GLM(Key.make("prostate_model"),"glm test simple poisson",params);
      model1 = job.trainModel().get();
      params._train = frMM._key;
      params._ignored_columns = new String[]{"X"};
      job = new GLM(Key.make("prostate_model"),"glm test simple poisson",params);
      model2 = job.trainModel().get();
      HashMap<String, Double> coefs1 = model1.coefficients();
      HashMap<String, Double> coefs2 = model2.coefficients();
      GLMValidation val1 = model1.validation();
      GLMValidation val2 = model2.validation();
      // compare against each other
      for(String s:coefs2.keySet()) {
        String s1 = s;
        if(s.startsWith("Origin"))
          s1 = "Origin." + s.substring(6);
        if(s.startsWith("Dest"))
          s1 = "Dest." + s.substring(4);
        if(s.startsWith("UniqueCarrier"))
          s1 = "UniqueCarrier." + s.substring(13);
        assertEquals("coeff " + s1 + " differs, " + coefs1.get(s1) + " != " + coefs2.get(s), coefs1.get(s1), coefs2.get(s),1e-4);
      DKV.put(frMM._key,frMM); // update the frame in the KV after removing the vec!
      }
      assertEquals(val1.nullDeviance(), val2.nullDeviance(),1e-4);
      assertEquals(val1.residualDeviance(), val2.residualDeviance(),1e-4);
      assertEquals(val1.aic(), val2.aic(),1e-4);
      // compare result against glmnet
      assertEquals(5336.918,val1.residualDeviance(),1);
      assertEquals(6051.613,val1.nullDeviance(),1);
      // lbfgs
//      params._solver = Solver.L_BFGS;
//      params._train = fr._key;
//      params._lambda = new double[]{.3};
//      job = new GLM(Key.make("lbfgs_cat"),"lbfgs glm built over categorical columns",params);
//      model3 = job.trainModel().get();
//      params._train = frMM._key;
//      job = new GLM(Key.make("lbfgs_mm"),"lbfgs glm built over pre-expanded categoricals (model.matrix)",params);
//      model4 = job.trainModel().get();
//      HashMap<String, Double> coefs3 = model3.coefficients();
//      HashMap<String, Double> coefs4 = model4.coefficients();
//      // compare against each other
//      for(String s:coefs4.keySet()) {
//        String s1 = s;
//        if(s.startsWith("Origin"))
//          s1 = "Origin." + s.substring(6);
//        if(s.startsWith("Dest"))
//          s1 = "Dest." + s.substring(4);
//        if(s.startsWith("UniqueCarrier"))
//          s1 = "UniqueCarrier." + s.substring(13);
//        assertEquals("coeff " + s1 + " differs, " + coefs3.get(s1) + " != " + coefs4.get(s), coefs3.get(s1), coefs4.get(s),1e-4);
//      }
    } finally {
      fr.delete();
      frMM.delete();
      if(model1 != null)model1.delete();
      if(model2 != null)model2.delete();
//      if(score != null)score.delete();
      if( job != null ) job.remove();
      Scope.exit();
    }
  }

  @Test public void testYmuTsk() {

  }
  /**
   * Simple test for binomial family (no regularization, test both lsm solvers).
   * Runs the classical prostate, using dataset with race replaced by categoricals (probably as it's supposed to be?), in any case,
   * it gets to test correct processing of categoricals.
   *
   * Compare against the results from standard R glm implementation.
   * @throws ExecutionException
   * @throws InterruptedException
   */
  @Test public void testProstate() throws InterruptedException, ExecutionException {
    GLM job = null;
    GLMModel model = null;
    Frame fr = parse_test_file("smalldata/glm_test/prostate_cat_replaced.csv");
    Frame score = null;
    try{
      Scope.enter();
      // R results
//      Coefficients:
//        (Intercept)           ID          AGE       RACER2       RACER3        DPROS        DCAPS          PSA          VOL      GLEASON
//          -8.894088     0.001588    -0.009589     0.231777    -0.459937     0.556231     0.556395     0.027854    -0.011355     1.010179
      String [] cfs1 = new String [] {"Intercept","AGE", "RACE.R2","RACE.R3", "DPROS", "DCAPS", "PSA", "VOL", "GLEASON"};
      double [] vals = new double [] {-8.14867, -0.01368, 0.32337, -0.38028, 0.55964, 0.49548, 0.02794, -0.01104, 0.97704};
      GLMParameters params = new GLMParameters(Family.binomial);
      params._response_column = "CAPSULE";
      params._ignored_columns = new String[]{"ID"};
      params._train = fr._key;
      params._lambda = new double[]{0};
      params._standardize = false;
      job = new GLM(Key.make("prostate_model"),"glm test simple poisson",params);
      model = job.trainModel().get();

      HashMap<String, Double> coefs = model.coefficients();
      for(int i = 0; i < cfs1.length; ++i)
        assertEquals(vals[i], coefs.get(cfs1[i]),1e-4);
      GLMValidation val = model.validation();
      assertEquals(512.3, val.nullDeviance(),1e-1);
      assertEquals(378.3, val.residualDeviance(),1e-1);
      assertEquals(396.3, val.aic(),1e-1);
      score = model.score(fr);

      hex.ModelMetricsBinomial mm = hex.ModelMetricsBinomial.getFromDKV(model,fr);
      hex.AUC2 adata = mm._auc;
      assertEquals(val.auc(), adata._auc, 1e-2);

//      GLMValidation val2 = new GLMValidationTsk(params,model._ymu,rank(model.beta())).doAll(new Vec[]{fr.vec("CAPSULE"),score.vec("1")})._val;
//      assertEquals(val.residualDeviance(),val2.residualDeviance(),1e-6);
//      assertEquals(val.nullDeviance(),val2.nullDeviance(),1e-6);
    } finally {
      fr.delete();
      if(model != null)model.delete();
      if(score != null)score.delete();
      if( job != null ) job.remove();
      Scope.exit();
    }
  }

  @Test public void testSynthetic() throws Exception {
    GLM job = null;
    GLMModel model = null;
    Frame fr = parse_test_file("smalldata/glm_test/glm_test2.csv");
    Frame score = null;
    try {
      Scope.enter();
      GLMParameters params = new GLMParameters(Family.binomial);
      params._response_column = "response";
      // params._response = fr.find(params._response_column);
      params._ignored_columns = new String[]{"ID"};
      params._train = fr._key;
      params._lambda = new double[]{0};
      params._standardize = false;
      params._max_iter = 20;
      job = new GLM(Key.make("glm_model"), "glm test simple poisson", params);
      model = job.trainModel().get();
      double [] beta = model.beta();
      System.out.println("beta = " + Arrays.toString(beta));
      assertEquals(model.validation().auc(), 1, 1e-4);
      GLMValidation val = model.validation();
      assertEquals(1,val.auc(),1e-2);
      score = model.score(fr);

      hex.ModelMetricsBinomial mm = hex.ModelMetricsBinomial.getFromDKV(model,fr);

      hex.AUC2 adata = mm._auc;
      assertEquals(val.auc(), adata._auc, 1e-2);
    } finally {
      fr.remove();
      if(model != null)model.delete();
      if(score != null)score.delete();
      if( job != null ) job.remove();
      Scope.exit();
    }
  }

  /**
   * Test strong rules on arcene datasets (10k predictors, 100 rows).
   * Should be able to obtain good model (~100 predictors, ~1 explained deviance) with up to 250 active predictors.
   * Scaled down (higher lambda min, fewer lambdas) to run at reasonable speed (whole test takes 20s on my laptop).
   *
   * Test runs glm with gaussian on arcene dataset and verifies it gets all lambda while limiting maximum actove predictors to reasonably small number.
   * Compares the objective value to expected one.
   */
  @Test public void testArcene() throws InterruptedException, ExecutionException{
    GLM job = null;
    Key parsed = Key.make("arcene_parsed");
    Key modelKey = Key.make("arcene_model");
    GLMModel model = null;
    Frame fr = parse_test_file(parsed, "smalldata/glm_test/arcene.csv");
    try{
      Scope.enter();
      GLMParameters params = new GLMParameters(Family.gaussian);
      // params._response = 0;
      params._response_column = fr._names[0];
      params._train = parsed;
      params._lambda_search = true;
      params._nlambdas = 35;
      params._lambda_min_ratio = 0.18;
      params._max_active_predictors = 200;
      params._alpha = new double[]{1};
      job = new GLM(modelKey,"glm test simple poisson",params);
      job.trainModel().get();
      model = DKV.get(modelKey).get();
      // assert on that we got all submodels (if strong rules work, we should be able to get the results with this many active predictors)
      assertEquals(params._nlambdas,model._output._submodels.length);
      GLMValidation val = model.validation();
      // assert on the quality of the result, technically should compare objective value, but this should be good enough for now
      model._output.setSubmodelIdx(model._output._submodels.length-1);
      Submodel sm = model._output._submodels[model._output._best_lambda_idx];
      double l1norm = 0;
      for(double d:sm.norm_beta) l1norm += Math.abs(d);
      double objval = sm.validation.residual_deviance / sm.validation.nobs + sm.lambda_value*l1norm;
      assertEquals(0.32922849120947384,objval,0.32922849120947384*1e-2);
      // test scoring on several submodels
      GLMModel m = new GetScoringModelTask(null,model._key,sm.lambda_value).invokeTask()._res;
      Frame score = m.score(fr);
      MSETsk mse = new MSETsk().doAll(score.anyVec(), fr.vec(m._output.responseName()));
      assertEquals(val.residualDeviance(),mse._resDev,1e-6);
      score.remove();
      // try scoring another model
      model._output.setSubmodelIdx(model._output._submodels.length>>1);
      sm = model._output._submodels[model._output._best_lambda_idx];
      val = model._output._submodels[model._output._best_lambda_idx].validation;
      m = new GetScoringModelTask(null,model._key,sm.lambda_value).invokeTask()._res;
      score = m.score(fr);
      mse = new MSETsk().doAll(score.anyVec(), fr.vec(m._output.responseName()));
      assertEquals(val.residualDeviance(),mse._resDev,1e-6);
      score.remove();
      job.remove();

      // test behavior when we can not fit within the active cols limit (should just bail out early and give us whatever it got)
      params = new GLMParameters(Family.gaussian);
      // params._response = 0;
      params._response_column = fr._names[0];
      params._train = parsed;
      params._lambda_search = true;
      params._nlambdas = 35;
      params._lambda_min_ratio = 0.18;
      params._max_active_predictors = 20;
      params._alpha = new double[]{1};
      job = new GLM(modelKey,"glm test simple poisson",params);
      job.trainModel().get();
      model = DKV.get(modelKey).get();
      assertTrue(model._output._submodels.length > 3);
      assertTrue(model.validation().residualDeviance() <= 93);
    } finally {
      fr.delete();
      if(model != null)model.delete();
      if( job != null ) job.remove();
      Scope.exit();
    }
  }


}
