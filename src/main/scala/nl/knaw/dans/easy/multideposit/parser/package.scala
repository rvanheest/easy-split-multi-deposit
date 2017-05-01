/**
 * Copyright (C) 2016 DANS - Data Archiving and Networked Services (info@dans.knaw.nl)
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
package nl.knaw.dans.easy.multideposit

import scala.language.implicitConversions

package object parser {

  type MultiDepositKey = String
  type DatasetId = String
  type DepositorId = String

  type DatasetRow = Map[MultiDepositKey, String]
  type DatasetRows = Seq[DatasetRow]

  // inspired by http://stackoverflow.com/questions/28223692/what-is-the-optimal-way-not-using-scalaz-to-type-require-a-non-empty-list
  type NonEmptyList[A] = ::[A]

  implicit def listToNEL[A](list: List[A]): NonEmptyList[A] = {
    require(list.nonEmpty, "the list can't be empty")
    ::(list.head, list.tail)
  }

  implicit class NELOps[A](val list: List[A]) extends AnyVal {
    def defaultIfEmpty(default: => A): NonEmptyList[A] = {
      if (list.isEmpty) List(default)
      else list
    }
  }

  implicit class DatasetRowFind(val row: DatasetRow) extends AnyVal {
    def find(name: MultiDepositKey): Option[String] = row.get(name).filterNot(_.isBlank)
  }
}
