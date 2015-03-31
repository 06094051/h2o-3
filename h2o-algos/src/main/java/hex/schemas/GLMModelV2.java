package hex.schemas;

import hex.glm.GLMModel;
import water.api.API;
import water.api.ModelOutputSchema;
import water.api.ModelSchema;
import water.api.TwoDimTableBase;
import water.util.ModelUtils;
//import water.util.DocGen.HTML;

public class GLMModelV2 extends ModelSchema<GLMModel, GLMModelV2, GLMModel.GLMParameters, GLMV2.GLMParametersV2, GLMModel.GLMOutput, GLMModelV2.GLMModelOutputV2> {

  public static final class GLMModelOutputV2 extends ModelOutputSchema<GLMModel.GLMOutput, GLMModelOutputV2> {
    // Output fields; input fields are in the parameters list

    // Submodel [] _submodels;

    @API(help="bets lambda if doing lambda search")
    int         best_lambda_idx;

    @API(help="The decision threshold to be used in classification; defaults to maximum_F1")
    double      threshold;

//    @API(help="fill me in GLMModelOutputV2")
//    double   [] global_beta;
//
//    @API(help="fill me in GLMModelOutputV2")
//    String   [] coefficient_names;

    @API(help="Table of coefficients")
    TwoDimTableBase coefficients_table;

    @API(help="Coefficient magnitudes")
    TwoDimTableBase coefficients_magnitude;

    @API(help="Residual Deviance - Training")
    double      residual_deviance;
      
    @API(help="Null Deviance - Training")
    double      null_deviance;
      
    @API(help="Residual Degrees of Freedom - Training")
    double      residual_degrees_of_freedom;
      
    @API(help="Null Degrees of Freedom - Training")
    double      null_degrees_of_freedom;

    @API(help="AIC - Training")
    double      aic;

    @API(help="AUC - Training")
    double      auc;

//    @API(help="Residual Deviance - Validation")
//    double      validation_residual_deviance;
//
//    @API(help="Null Deviance - Validation")
//    double      validation_null_deviance;
//
//    @API(help="Residual Degrees of Freedom - Validation")
//    double      validation_residual_degrees_of_freedom;
//
//    @API(help="Null Degrees of Freedom - Validation")
//    double      validation_null_degrees_of_freedom;

    @API(help="fill me in GLMModelOutputV2; I think I'm redundant")
    boolean binomial; // TODO: isn't this redundant, given model_category?

    @API(help="fill me in GLMModelOutputV2")
    int rank;

    @Override
    public GLMModelOutputV2 fillFromImpl(GLMModel.GLMOutput impl) {
      super.fillFromImpl(impl);
      this.rank = impl.rank();
      return this;
    }
  } // GLMModelOutputV2

  // TOOD: I think we can implement the following two in ModelSchema, using reflection on the type parameters.
  public GLMV2.GLMParametersV2 createParametersSchema() { return new GLMV2.GLMParametersV2(); }
  public GLMModelOutputV2 createOutputSchema() { return new GLMModelOutputV2(); }

  // TODO: revisit; we want an empty impl here. . .
  @Override public GLMModel createImpl() {
    GLMModel.GLMParameters parms = parameters.createImpl();
    return new GLMModel( key.key(), parms, new GLMModel.GLMOutput(), null, 0.0, 0.0, 0);
  }
}
