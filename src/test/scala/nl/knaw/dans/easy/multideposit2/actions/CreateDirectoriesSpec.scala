/**
 * Copyright (C) 2016 DANS - Data Archiving and Networked Services (info@dans.knaw.nl)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nl.knaw.dans.easy.multideposit2.actions

import java.nio.file.{ Files, Path }

import nl.knaw.dans.easy.multideposit.FileExtensions
import nl.knaw.dans.easy.multideposit2.PathExplorer.{ InputPathExplorer, StagingPathExplorer }
import nl.knaw.dans.easy.multideposit2.TestSupportFixture
import org.scalatest.BeforeAndAfterEach

import scala.util.Success

class CreateDirectoriesSpec extends TestSupportFixture with BeforeAndAfterEach {
  self =>

  private val depositId = "dsId1"
  private val action = new CreateDirectories with StagingPathExplorer with InputPathExplorer {
    override val multiDepositDir: Path = self.multiDepositDir
    override val stagingDir: Path = self.stagingDir
  }

  override def beforeEach(): Unit = {
    super.beforeEach()

    // create depositDir base directory
    stagingDir.deleteDirectory()
    Files.createDirectory(stagingDir)
    stagingDir.toFile should exist
  }

  "createDepositDirectories" should "create the staging directories if they do not yet exist" in {
    stagingDir(depositId).toFile shouldNot exist
    stagingBagDir(depositId).toFile shouldNot exist

    action.createDepositDirectories(depositId) shouldBe a[Success[_]]

    stagingDir(depositId).toFile should exist
    stagingBagDir(depositId).toFile should exist
  }

  "createMetadataDirectory" should "create the metadata directory inside the bag directory" in {
    stagingBagMetadataDir(depositId).toFile shouldNot exist

    action.createMetadataDirectory(depositId) shouldBe a[Success[_]]

    stagingBagMetadataDir(depositId).toFile should exist
  }

  "discardDeposit" should "delete the bag directory in the staging area in case it exists" in {
    action.createDepositDirectories(depositId) shouldBe a[Success[_]]
    action.createMetadataDirectory(depositId) shouldBe a[Success[_]]
    stagingDir(depositId).toFile should exist
    stagingBagDir(depositId).toFile should exist
    stagingBagMetadataDir(depositId).toFile should exist

    action.discardDeposit(depositId) shouldBe a[Success[_]]
    stagingBagMetadataDir(depositId).toFile shouldNot exist
    stagingBagDir(depositId).toFile shouldNot exist
    stagingDir(depositId).toFile shouldNot exist
  }

  it should "do nothing if the bag directory doesn't exist in the staging area" in {
    stagingDir(depositId).toFile shouldNot exist
    stagingBagDir(depositId).toFile shouldNot exist
    stagingBagMetadataDir(depositId).toFile shouldNot exist

    action.discardDeposit(depositId) shouldBe a[Success[_]]
    stagingBagMetadataDir(depositId).toFile shouldNot exist
    stagingBagDir(depositId).toFile shouldNot exist
    stagingDir(depositId).toFile shouldNot exist
  }
}
