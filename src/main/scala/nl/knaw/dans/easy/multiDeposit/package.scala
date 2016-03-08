/**
 * Copyright (C) 2015-2016 DANS - Data Archiving and Networked Services (info@dans.knaw.nl)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nl.knaw.dans.easy

import java.io.{File, IOException}
import java.util.Properties

import org.apache.commons.io.FileUtils
import org.apache.commons.lang.StringUtils
import rx.lang.scala.Observable
import rx.lang.scala.ObservableExtensions

import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.collection.JavaConversions.collectionAsScalaIterable
import scala.util.{Failure, Success, Try}

package object multideposit {

  type DatasetID = String
  type MultiDepositKey = String
  type MultiDepositValues = List[String]
  type Dataset = mutable.HashMap[MultiDepositKey, MultiDepositValues]
  type Datasets = ListBuffer[(DatasetID, Dataset)]

  case class FileParameters(row: Option[Int], sip: Option[String], dataset: Option[String],
                            storageService: Option[String], storagePath: Option[String],
                            audioVideo: Option[String])
  case class Settings(appHomeDir: File = null,
                      multidepositDir: File = null,
                      springfieldInbox: File = null,
                      outputDepositDir: File = null) {
    override def toString: String =
      s"Settings(home=$appHomeDir, multideposit-dir=$multidepositDir, " +
        s"springfield-inbox=$springfieldInbox, " +
        s"deposit-dir=$outputDepositDir)"
  }

  case class ActionException(row: Int, message: String, cause: Throwable = null) extends RuntimeException(message, cause)

  implicit class StringExtensions(val s: String) extends AnyVal {
    /**
      * Checks whether the `String` is blank
      * (according to [[org.apache.commons.lang.StringUtils.isBlank]])
      *
      * @return
      */
    def isBlank = StringUtils.isBlank(s)

    /** Converts a `String` to an `Option[String]`. If the `String` is blank
      * (according to [[org.apache.commons.lang.StringUtils.isBlank]])
      * the empty `Option` is returned, otherwise the `String` is returned
      * wrapped in an `Option`.
      *
      * @return an `Option` of the input string that indicates whether it is blank
      */
    def toOption = if (s.isBlank) Option.empty else Option(s)

    /** Converts a `String` into an `Option[Int]` if it is not blank
      * (according to [[org.apache.commons.lang.StringUtils.isBlank]]).
      * Strings that do not represent a number will yield an empty `Option`.
      *
      * @return an `Option` of the input string, converted as a number if it is not blank
      */
    def toIntOption = {
      Try {
        if (s.isBlank) Option.empty
        else Option(s.toInt)
      } getOrElse Option.empty
    }
  }

  object Version {
    def apply(): String = {
      val properties = new Properties()
      properties.load(getClass.getResourceAsStream("/Version.properties"))
      properties.getProperty("process-sip.version")
    }
  }

  implicit class TryExceptionHandling[T](val t: Try[T]) extends AnyVal {
    /** Terminating operator for `Try` that converts the `Failure` case in a value.
      *
      * @param handle converts `Throwable` to a value of type `T`
      * @return either the value inside `Try` (on success) or the result of `handle` (on failure)
      */
    def onError[S >: T](handle: Throwable => S): S = {
      t match {
        case Success(value) => value
        case Failure(throwable) => handle(throwable)
      }
    }
  }

  implicit class OptionExtensions[T](val option: Option[T]) extends AnyVal {
    /**
      * Converts an `Option` to an `Observable`. `Some` is mapped to an `Observable` with 1 element,
      * `None` is mapped to the empty `Observable`.
      *
      * @return an `Observable` with either zero or one elements
      */
    def toObservable = option.map(Observable.just(_)).getOrElse(Observable.empty)
  }

  implicit class FileExtensions(val file: File) extends AnyVal {
    /**
      * Writes a CharSequence to a file creating the file if it does not exist using the default encoding for the VM.
      *
      * @param data the content to write to the file
      * @throws IOException in case of an I/O error
      */
    def write(data: String) = FileUtils.write(file, data)

    /**
      * Appends a CharSequence to a file creating the file if it does not exist using the default encoding for the VM.
      *
      * @param data the content to write to the file
      * @throws IOException in case of an I/O error
      */
    def append(data: String) = FileUtils.write(file, data, true)

    /**
      * Reads the contents of a file into a String using the default encoding for the VM.
      * The file is always closed.
      *
      * @return the file contents, never ``null``
      * @throws IOException in case of an I/O error
      */
    def read = FileUtils.readFileToString(file)

    /**
      * Determines whether the ``parent`` directory contains the ``child`` element (a file or directory).
      * <p>
      * Files are normalized before comparison.
      * </p>
      *
      * Edge cases:
      * <ul>
      * <li>A ``directory`` must not be null: if null, throw IllegalArgumentException</li>
      * <li>A ``directory`` must be a directory: if not a directory, throw IllegalArgumentException</li>
      * <li>A directory does not contain itself: return false</li>
      * <li>A null child file is not contained in any parent: return false</li>
      * </ul>
      *
      * @param child the file to consider as the child.
      * @return true is the candidate leaf is under by the specified composite. False otherwise.
      * @throws IOException if an IO error occurs while checking the files.
      */
    def directoryContains(child: File) = FileUtils.directoryContains(file, child)

    /**
      * Copies a whole directory to a new location preserving the file dates.
      * <p>
      * This method copies the specified directory and all its child
      * directories and files to the specified destination.
      * The destination is the new location and name of the directory.
      * <p>
      * The destination directory is created if it does not exist.
      * If the destination directory did exist, then this method merges
      * the source with the destination, with the source taking precedence.
      * <p>
      * <strong>Note:</strong> This method tries to preserve the files' last
      * modified date/times using [[File#setLastModified(long)]], however
      * it is not guaranteed that those operations will succeed.
      * If the modification operation fails, no indication is provided.
      *
      * @param destDir the new directory, must not be ``null``
      * @throws NullPointerException if source or destination is ``null``
      * @throws IOException if source or destination is invalid
      * @throws IOException if an IO error occurs during copying
      */
    def copyFile(destDir: File) = FileUtils.copyFile(file, destDir)

    /**
      * Copies a whole directory to a new location preserving the file dates.
      * <p>
      * This method copies the specified directory and all its child
      * directories and files to the specified destination.
      * The destination is the new location and name of the directory.
      * <p>
      * The destination directory is created if it does not exist.
      * If the destination directory did exist, then this method merges
      * the source with the destination, with the source taking precedence.
      * <p>
      * <strong>Note:</strong> This method tries to preserve the files' last
      * modified date/times using ``File#setLastModified(long)``, however
      * it is not guaranteed that those operations will succeed.
      * If the modification operation fails, no indication is provided.
      *
      * @param destDir the new directory, must not be ``null``
      * @throws NullPointerException if source or destination is ``null``
      * @throws IOException if source or destination is invalid
      * @throws IOException if an IO error occurs during copying
      */
    def copyDir(destDir: File) = FileUtils.copyDirectory(file, destDir)

    /**
      * Deletes a directory recursively.
      *
      * @throws IOException in case deletion is unsuccessful
      */
    def deleteDirectory() = FileUtils.deleteDirectory(file)

    /**
      * Finds files within a given directory and its subdirectories.
      *
      * @return a ``List`` of ``java.io.File`` with the files
      */
    def listRecursively = FileUtils.listFiles(file, null, true).toList
  }

  implicit class DatasetExtensions(val dataset: Dataset) extends AnyVal {
    /**
      * Retrieves the value of a certain parameter from the dataset on a certain row.
      * If either the key is not present, the specified row does not exist or the value `blank`
      * (according to [[org.apache.commons.lang.StringUtils.isBlank]]), then [[Option.empty]]
      * is returned.
      *
      * @param key the key under which the value is stored in the dataset
      * @param row the row on which the value occurs
      * @return the value belonging to the (key, row) pair if present, else [[Option.empty]]
      */
    def getValue(key: MultiDepositKey)(row: Int) = {
      for {
        values <- dataset.get(key)
        value <- Try(values(row)).toOption
        value2 <- value.toOption
      } yield value2
    }
  }

  val bagDirName = "bag"
  val dataDirName = "data"
  val metadataDirName = "metadata"
  val instructionsFileName = "instructions.csv"
  val datasetMetadataFileName = "dataset.xml"
  val fileMetadataFileName = "files.xml"
  val propsFileName = "deposit.properties"
  val springfieldActionsFileName = "springfield-actions.xml"

  def multiDepositDir(settings: Settings, datasetID: DatasetID) = {
    new File(settings.multidepositDir, datasetID)
  }
  def multiDepositInstructionsFile(settings: Settings) = {
    new File(settings.multidepositDir, instructionsFileName)
  }
  def outputDepositDir(settings: Settings, datasetID: DatasetID) = {
    new File(settings.outputDepositDir, s"${settings.multidepositDir.getName}-$datasetID")
  }
  def outputDepositBagDir(settings: Settings, datasetID: DatasetID) = {
    new File(outputDepositDir(settings, datasetID), bagDirName)
  }
  def outputDepositBagDataDir(settings: Settings, datasetID: DatasetID) = {
    new File(outputDepositBagDir(settings, datasetID), dataDirName)
  }
  def outputDepositBagMetadataDir(settings: Settings, datasetID: DatasetID) = {
    new File(outputDepositBagDir(settings, datasetID), metadataDirName)
  }
  def outputPropertiesFile(settings: Settings, datasetID: DatasetID) = {
    new File(outputDepositDir(settings, datasetID), propsFileName)
  }
  def outputDatasetMetadataFile(settings: Settings, datasetID: DatasetID) = {
    new File(outputDepositBagMetadataDir(settings, datasetID), datasetMetadataFileName)
  }
  def outputFileMetadataFile(settings: Settings, datasetID: DatasetID) = {
    new File(outputDepositBagMetadataDir(settings, datasetID), fileMetadataFileName)
  }
  def springfieldInboxDir(settings: Settings, fileMd: String) = {
    new File(settings.springfieldInbox, fileMd)
  }
  def springfieldInboxActionsFile(settings: Settings) = {
    springfieldInboxDir(settings, springfieldActionsFileName)
  }

  /** Extract the ''file parameters'' from a dataset and return these in an ``Observable``.
    * The following parameters are used for this: '''ROW''', '''FILE_SIP''', '''FILE_DATASET''',
    * '''FILE_STORAGE_SERVICE''', '''FILE_STORAGE_PATH''', '''FILE_AUDIO_VIDEO'''.
    *
    * @param dataset the dataset from which the file parameters get extracted
    * @return the ``Observable`` with fileparameters values extracted from the dataset
    */
  def extractFileParameters(dataset: Dataset) = {
    Observable.just("ROW", "FILE_SIP", "FILE_DATASET", "FILE_STORAGE_SERVICE", "FILE_STORAGE_PATH", "FILE_AUDIO_VIDEO")
      .flatMap(dataset.get(_).toObservable)
      .take(1)
      .flatMap(xs => xs.indices.toObservable
        .map(index => {
          def valueAt(key: String): Option[String] = {
            dataset.get(key).flatMap(_(index).toOption)
          }
          def intAt(key: String): Option[Int] = {
            dataset.get(key).flatMap(_(index).toIntOption)
          }

          FileParameters(intAt("ROW"), valueAt("FILE_SIP"), valueAt("FILE_DATASET"),
            valueAt("FILE_STORAGE_SERVICE"), valueAt("FILE_STORAGE_PATH"),
            valueAt("FILE_AUDIO_VIDEO"))
        })
        .filter {
          case FileParameters(_, None, None, None, None, None) => false
          case _ => true
        })
  }

  /** Generates an error report with a `heading` and a list of `ActionException`s coming from a
    * list of `Try`s, sorted by row number. Supplying other exceptions than `ActionException` will
    * cause an `AssertionError`. `Success` input in `trys` are ignored.
    *
    * @param heading a piece of text before the list of errors
    * @param trys the failures to be reported
    * @return the error report
    */
  def generateErrorReport[T](header: String = "", trys: Seq[Try[T]] = Nil, footer: String = ""): String = {
    header.toOption.map(s => s"$s\n").getOrElse("") +
      trys.filter(_.isFailure)
        .map {
          case Failure(actionEx: ActionException) => actionEx
          // TODO add other Failure(exceptions) cases if needed and adjust the error message below accordingly
          case _ => throw new AssertionError("Only Failures of ActionException are expected here")
        }
        .sortBy(_.row)
        .map(actionEx => s" - row ${actionEx.row}: ${actionEx.message}")
        .mkString("\n") +
      footer.toOption.map(s => if (trys.isEmpty) s else s"\n$s").getOrElse("")
  }
}
