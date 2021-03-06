/*
 * DataSource.scala
 *
 * Overview Project
 * Created by Jonas Karlsson, Aug 2012
 */

package org.overviewproject.database

import com.jolbox.bonecp._
import java.sql.Connection
import javax.sql.{ DataSource => JDataSource }
import org.slf4j.LoggerFactory

trait DataSource {
  def getConnection: Connection
  def shutdown: Unit
  def getDataSource: JDataSource
}

object DataSource {
  /** Adapter for an existing DataSource.
    *
    * This adapter does not implement shutdown(): we assume the caller takes
    * care of that.
    */
  def apply(dataSource: JDataSource) = new DataSource {
    override def getConnection: Connection = dataSource.getConnection()
    override def shutdown: Unit = {}
    override def getDataSource: JDataSource = dataSource
  }

  /**
   * Wrapper for BoneCPDataSource, that applies the given configuration.
   * Should be shutdown() when program ends to shutdown BoneCP connection pool.
   */
  def apply(configuration: DatabaseConfiguration) = new DataSource {
    Class.forName(configuration.databaseDriver) // initialize

    private val dataSource = new BoneCPDataSource()

    dataSource.setJdbcUrl(configuration.databaseUrl)
    dataSource.setUsername(configuration.username)
    dataSource.setPassword(configuration.password)
    dataSource.setMinConnectionsPerPartition(1)
    dataSource.setMaxConnectionsPerPartition(5)
    dataSource.setAcquireIncrement(1)
    dataSource.setPartitionCount(1)
    dataSource.setDisableJMX(true)
    dataSource.setLogStatementsEnabled(LoggerFactory.getLogger(classOf[BoneCPConfig]).isDebugEnabled())

    override def getConnection: Connection = dataSource.getConnection()
    override def shutdown: Unit = dataSource.close()
    override def getDataSource: JDataSource = dataSource
  }
}
