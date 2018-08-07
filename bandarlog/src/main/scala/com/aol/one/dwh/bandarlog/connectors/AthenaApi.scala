package com.aol.one.dwh.bandarlog.connectors

import java.sql._
import java.text.SimpleDateFormat
import java.time.Duration

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

  val db_name = "Athena"

  private def fetchAll(rs: ResultSet): List[String] = {
    Iterator
      .continually(rs.next)
      .takeWhile(identity)
      .map { _ => rs.getString(1) }
      .toList
  }

  def get_table_location(table_name: String): Option[String] = {
    val stmt = connection.createStatement()
    try {
      logger.info(s"Request table $table_name definition to extract location.")
      val sql = s"SHOW CREATE TABLE $db_name.$table_name"
      logger.info(s"Running query:[$sql].")
      val rs: ResultSet = stmt.executeQuery(sql)
      val location = fetchAll(rs).filter(x => x.contains("s3://"))
      rs.close()
      Some(location.head.trim.replace("\'", ""))
    } catch {
      case ex: SQLException =>
        logger.error(s"Table $table_name was not found.")
        None
    } finally {
      stmt.close()
    }
  }

  def set_table_location(table_name: String, new_location: String): Unit = {
    val stmt = connection.createStatement()
    try {
      logger.info(s"Set new table $table_name location $new_location")
      val sql = s"ALTER TABLE $db_name.$table_name SET LOCATION '$new_location'"
      logger.info(s"Running query:[$sql]")
      stmt.execute(sql)
    } catch {
      case ex: SQLException =>
        logger.error(s"Cannot set new location, error: ${ex.getMessage}.")
    } finally {
      stmt.close()
    }
  }

  def recover_all_partitions(table_name: String): Unit = {
    val stmt = connection.createStatement()
    try {
      val sql = s"MSCK REPAIR TABLE $db_name.$table_name"
      logger.info(s"Running query:[$sql].")
      stmt.execute(sql)
    } catch {
      case ex: SQLException =>
        logger.error(s"Cannot load partitions, error: ${ex.getMessage}.")
    } finally {
      stmt.close()
    }
  }

  def check_if_table_exists(table_name: String): Option[Boolean] = {
    val stmt = connection.createStatement()
    try {
      logger.info(s"Check if table $table_name exists in Athena.")
      val sql = s"SELECT * FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = '$table_name'"
      logger.info(s"Running query:[$sql].")
      val rs: ResultSet = stmt.executeQuery(sql)
      val check = rs.next
      rs.close()
      Some(check)
    } catch {
      case ex: SQLException =>
        logger.error(s"Table $table_name does not exist.")
        None
    } finally {
      stmt.close()
    }
  }

  def clean_table(table_name: String, table_window_in_millis: Long): Unit = {
    val stmt = connection.createStatement()
    try {
      val deltaTime: Duration = Duration.ofDays(1).plusMillis(table_window_in_millis)
      val timediff = System.currentTimeMillis() - deltaTime.toMillis
      val dateFormat = new SimpleDateFormat("YYYY-mm-dd")
      val date = dateFormat.format(timediff)
      val sql = s"ALTER TABLE $db_name.$table_name DROP PARTITION(date='$date')"
      logger.info(s"Running query:[$sql].")
      stmt.execute(sql)
    } catch {
      case ex: SQLException =>
        logger.error(s"cannot clean table $table_name, error: ${ex.getMessage}.")
    } finally {
      stmt.close()
    }
  }


}
