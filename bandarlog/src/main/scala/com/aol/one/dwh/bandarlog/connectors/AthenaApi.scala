package com.aol.one.dwh.bandarlog.connectors

import java.sql._

import com.aol.one.dwh.infra.util.LogTrait

class AthenaApi extends LogTrait {

  private val DB_DRIVER: String = "com.simba.athena.jdbc.Driver"
  private val DB_CONNECTION: String = "jdbc:awsathena://AwsRegion=eu-central-1;" +
    "UID=xxx;" +
    "PWD=xxx;" +
    "S3OutputLocation=s3://xxx;"
  Class.forName(DB_DRIVER)
  val connection: Connection = DriverManager.getConnection(DB_CONNECTION)
  val stmt: Statement = connection.createStatement()

  private def fetchAll(rs: ResultSet): List[String] = {
    Iterator
      .continually(rs.next)
      .takeWhile(identity)
      .map { _ => rs.getString(1) }
      .toList
  }

  def get_table_location(table_name: String, db_name: String): Option[String] = {
    val stmt = connection.createStatement()
    try {
      val sql = s"SHOW CREATE TABLE $db_name.$table_name"
      logger.info(s"Running query:[$sql]")
      val rs: ResultSet = stmt.executeQuery(sql)
      val location = fetchAll(rs).filter(x => x.contains("s3://"))
      rs.close()
      Some(location.head.trim.replace("\'", ""))
    } catch {
      case ex: SQLException =>
        logger.error(s"Table $table_name was not found")
        None
    } finally {
      stmt.close()
    }
  }

  def set_table_location(table_name: String, new_location: String, db_name: String): Unit = {
    val stmt = connection.createStatement()
    try {
      val sql = s"ALTER TABLE $db_name.$table_name SET LOCATION '$new_location'"
      stmt.execute(sql)
    } catch {
      case ex: SQLException =>
        logger.error(s"Cannot set new location, error: ${ex.getMessage}")
    } finally {
      stmt.close()
    }
  }

  def recover_all_partitions(table_name: String, db_name: String): Unit = {
    val stmt = connection.createStatement()
    try {
      val sql = s"MSCK REPAIR TABLE $db_name.$table_name"
      stmt.execute(sql)
    } catch {
      case ex: SQLException =>
        logger.error(s"Cannot load partitions, error: ${ex.getMessage}")
    } finally {
      stmt.close()
    }
  }

}