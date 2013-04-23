///////////////////////////////////////////////////////////////////////////////
//  FieldspringInfo.scala
//
//  Copyright (C) 2011 Ben Wing, The University of Texas at Austin
//
//  Licensed under the Apache License, Version 2.0 (the "License");
//  you may not use this file except in compliance with the License.
//  You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
//  Unless required by applicable law or agreed to in writing, software
//  distributed under the License is distributed on an "AS IS" BASIS,
//  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//  See the License for the specific language governing permissions and
//  limitations under the License.
///////////////////////////////////////////////////////////////////////////////

package opennlp.fieldspring.gridlocate

import opennlp.fieldspring.util.printutil.errprint

/**
 Fieldspring-specific information (e.g. env vars).
 */

object FieldspringInfo {
  var fieldspring_dir: String = null

  def set_fieldspring_dir(dir: String) {
    fieldspring_dir = dir
  }

  def get_fieldspring_dir() = {
    if (fieldspring_dir == null)
      fieldspring_dir = System.getenv("FIELDSPRING_DIR")
    if (fieldspring_dir == null) {
      errprint("""FIELDSPRING_DIR must be set to the top-level directory where
Fieldspring is installed.""")
      require(fieldspring_dir != null)
    }
    fieldspring_dir
  }
}
