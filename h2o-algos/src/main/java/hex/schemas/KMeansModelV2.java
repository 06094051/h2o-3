package hex.schemas;

import hex.kmeans.KMeansModel;
import water.api.API;
import water.api.ModelOutputSchema;
import water.api.ModelSchema;
import water.api.TwoDimTableV1;
//import water.util.DocGen.HTML;

public class KMeansModelV2 extends ModelSchema<KMeansModel, KMeansModelV2, KMeansModel.KMeansParameters, KMeansV2.KMeansParametersV2, KMeansModel.KMeansOutput, KMeansModelV2.KMeansModelOutputV2> {

  public static final class KMeansModelOutputV2 extends ModelOutputSchema<KMeansModel.KMeansOutput, KMeansModelOutputV2> {
    // Output fields; input fields are in the parameters list
    @API(help="Cluster Centers[k][features]")
    public TwoDimTableV1 centers;

    @API(help="Cluster Centers[k][features] on Standardized Data")
    public TwoDimTableV1 centers_std;
    
    @API(help="Cluster Size[k]")
    public long[/*k*/] size;

    @API(help="Within cluster Mean Square Error per cluster")
    public double[/*k*/] within_mse;   // Within-cluster MSE, variance

    @API(help="Average within cluster Mean Square Error")
    public double avg_within_ss;       // Average within-cluster MSE, variance

    @API(help="Average Mean Square Error to grand mean")
    public double avg_ss;    // Total MSE to grand mean centroid

    @API(help="Average between cluster Mean Square Error")
    public double avg_between_ss;

    @API(help="Iterations executed")
    public double iterations;

    @API(help="Number of categorical columns trained on")
    public int categorical_column_count;

  } // KMeansModelOutputV2

  // TOOD: I think we can implement the following two in ModelSchema, using reflection on the type parameters.
  public KMeansV2.KMeansParametersV2 createParametersSchema() { return new KMeansV2.KMeansParametersV2(); }
  public KMeansModelOutputV2 createOutputSchema() { return new KMeansModelOutputV2(); }

  //==========================
  // Custom adapters go here

  // Version&Schema-specific filling into the impl
  @Override public KMeansModel createImpl() {
    KMeansModel.KMeansParameters parms = parameters.createImpl();
    return new KMeansModel( key.key(), parms, null );
  }
}
