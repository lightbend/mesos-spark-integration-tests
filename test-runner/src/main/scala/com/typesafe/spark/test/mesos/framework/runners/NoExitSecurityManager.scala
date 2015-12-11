package com.typesafe.spark.test.mesos.framework.runners

import java.security.Permission

import scala.collection.JavaConverters._

/**
 * Don't allow System.exit, instead throw an exception. Useful when running
 * tests.
 */
object NoExitSecurityManager extends SecurityManager {

  override def checkExit(status: Int): Unit = {
    super.checkExit(status)

    throw ExitException(status)
  }

  override def checkPermission(perm: Permission): Unit = {
    // allow everything
  }

  override def checkPermission(perm: Permission, context: Object): Unit = {
    // allow everything
  }

}

case class ExitException(code: Int)
  extends SecurityException(s"Application tried to exit with code $code")
