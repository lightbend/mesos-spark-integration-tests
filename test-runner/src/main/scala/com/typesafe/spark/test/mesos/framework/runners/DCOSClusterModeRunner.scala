package com.typesafe.spark.test.mesos.framework.runners

import Utils._
import DCOSUtils._
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.{CannedAccessControlList, ObjectMetadata, PutObjectRequest}
import com.typesafe.config.Config
import java.io.{FileInputStream, File}

import org.apache.commons.io.FilenameUtils
;

object DCOSClusterModeRunner {
  def run(applicationJarPath : String)(implicit config: Config): String = {
    uploadToS3(applicationJarPath)

    val basename = FilenameUtils.getBaseName(applicationJarPath)
    val extension = FilenameUtils.getExtension(applicationJarPath)
    val S3_BUCKET = config.getString("aws.s3.bucket")
    val S3_PREFIX = config.getString("aws.s3.prefix")

    val s3URL = s"http://${S3_BUCKET}.s3.amazonaws.com/${S3_PREFIX}${basename}.${extension}"

    printMsg("Submitting spark tests...")
    val submissionId = submitSparkJobDCOS(s3URL)

    printMsg(s"Waiting for spark tests with task ID ${submissionId} to complete...")
    waitForSparkJobDCOS(submissionId)

    printMsg("Getting spark test output...")
    getLogOutputDCOS(submissionId)
  }

  private def uploadToS3(path : String)(implicit config: Config): Unit = {
    printMsg("Uploading JAR to S3...")
    val inputStream = new FileInputStream(path)
    val basename = FilenameUtils.getBaseName(path)
    val extension = FilenameUtils.getExtension(path)

    val AWS_ACCESS_KEY = config.getString("aws.access_key")
    val AWS_SECRET_KEY = config.getString("aws.secret_key")
    val S3_BUCKET = config.getString("aws.s3.bucket")
    val S3_PREFIX = config.getString("aws.s3.prefix")
    val awsCreds = new BasicAWSCredentials(AWS_ACCESS_KEY, AWS_SECRET_KEY)
    val awsClient = new AmazonS3Client(awsCreds)

    val metadata = new ObjectMetadata()
    metadata.setContentType("application/java-archive")

    val request = new PutObjectRequest(
      S3_BUCKET,
      s"${S3_PREFIX}${basename}.${extension}",
      inputStream,
      metadata
    )
    request.setCannedAcl(CannedAccessControlList.PublicRead)

    awsClient.putObject(request)
  }
}
