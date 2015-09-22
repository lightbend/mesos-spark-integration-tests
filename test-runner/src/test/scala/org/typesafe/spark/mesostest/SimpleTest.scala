package org.typesafe.spark.mesostest

import org.scalatest.FunSuite
import org.apache.spark.SparkContext
import org.apache.spark.SparkConf

class SimpleTest extends FunSuite {
  
  import SimpleTest._
  
  test("simple count") {
    runSparkTest { sc =>
      val rdd = sc.makeRDD(1 to 5)
      val res = rdd.sum()
      
      assert(15 == res)
    }
  }

}

object SimpleTest {
  
  def runSparkTest(t: (SparkContext) => Unit) {
    val sparkConf= new SparkConf()
      .setAppName("Mesos integration test")
      .setMaster(System.getProperty("spark.master"))
    val sc = new SparkContext(sparkConf)
    try {
    	t(sc)
    } finally {
      sc.stop()
    }
    
  }
  
}