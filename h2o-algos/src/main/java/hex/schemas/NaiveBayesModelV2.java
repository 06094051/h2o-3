package hex.schemas;

import hex.naivebayes.NaiveBayesModel;
import water.api.API;
import water.api.ModelOutputSchema;
import water.api.ModelSchema;
import water.api.TwoDimTableBase;

public class NaiveBayesModelV2 extends ModelSchema<NaiveBayesModel, NaiveBayesModelV2, NaiveBayesModel.NaiveBayesParameters, NaiveBayesV2.NaiveBayesParametersV2, NaiveBayesModel.NaiveBayesOutput, NaiveBayesModelV2.NaiveBayesModelOutputV2> {
  public static final class NaiveBayesModelOutputV2 extends ModelOutputSchema<NaiveBayesModel.NaiveBayesOutput, NaiveBayesModelOutputV2> {
    // Output fields; input fields are in the parameters list
    @API(help = "Model parameters")
    NaiveBayesV2.NaiveBayesParametersV2 parameters;

    @API(help = "Categorical levels of the response")
    public String[] levels;

    @API(help = "A-priori probabilities of the response")
    public TwoDimTableBase apriori;

    @API(help = "Conditional probabilities of the predictors")
    public TwoDimTableBase[] pcond;
  }

  // TODO: I think we can implement the following two in ModelSchema, using reflection on the type parameters.
  public NaiveBayesV2.NaiveBayesParametersV2 createParametersSchema() { return new NaiveBayesV2.NaiveBayesParametersV2(); }
  public NaiveBayesModelOutputV2 createOutputSchema() { return new NaiveBayesModelOutputV2(); }

  // Version&Schema-specific filling into the impl
  @Override public NaiveBayesModel createImpl() {
    NaiveBayesModel.NaiveBayesParameters parms = parameters.createImpl();
    return new NaiveBayesModel( key.key(), parms, null );
  }
}
