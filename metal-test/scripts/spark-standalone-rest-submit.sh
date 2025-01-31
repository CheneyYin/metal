rest_host=$1
curl -X POST http://$rest_host:6066/v1/submissions/create --header "Content-Type:application/json;charset=UTF-8" --data \
'
{
  "appResource": "/home/spark/metal/metal-backend-1.0.0-SNAPSHOT.jar",
  "sparkProperties": {
    "spark.executor.userClassPathFirst": "true",
    "spark.driver.userClassPathFirst": "true",
    "spark.master": "spark://master-0.spark.metal.org:7077",
    "spark.app.name": "Spark REST API - Metal-Backend",
    "spark.submit.deployMode": "cluster",
    "spark.eventLog.enabled": "true",
    "spark.eventLog.dir": "hdfs://namenode.hdfs.metal.org:9000/shared/spark-logs",
    "spark.jars": "/home/spark/metal/metal-backend-1.0.0-SNAPSHOT.jar"
  },
  "clientSparkVersion": "3.3.0",
  "mainClass": "org.metal.backend.BackendLauncher",
  "environmentVariables": {
    "SPARK_ENV_LOADED": "1"
  },
  "action": "CreateSubmissionRequest",
  "appArgs": [
    "--interactive-mode",
    "--deploy-id", "35c0dbd3-0700-493f-a071-343bc0300bc9",
    "--deploy-epoch", "0",
    "--report-service-address", "report.metal.org",
    "--rest-api-port", "18000"
  ]
}
'