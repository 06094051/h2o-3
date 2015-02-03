import sys
import h2o

def ozoneKM(ip, port):
  # Connect to a pre-existing cluster
  h2o.init(ip, port)  # connect to localhost:54321

  train = h2o.import_frame(path="smalldata/glm_test/ozone.csv")

  # See that the data is ready
  print train.describe()

  # Run KMeans
  my_km = h2o.kmeans(x=train,
                     k=10,
                     init = "PlusPlus",
                     max_iterations = 100)

  my_km.show()
  my_km.summary()

  my_pred = my_km.predict(train)
  my_pred.describe()

if __name__ == "__main__":
  args = sys.argv
  print args
  if len(args) > 1:
      ip = args[1]
      port = int(args[2])
  else:
      ip = "localhost"
      port = 54321
  ozoneKM(ip, port)
