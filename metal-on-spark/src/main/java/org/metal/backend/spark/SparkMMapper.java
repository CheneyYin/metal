/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.metal.backend.spark;

import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.metal.core.MMapper;
import org.metal.core.props.IMMapperProps;

public abstract class SparkMMapper<P extends IMMapperProps> extends
    MMapper<Dataset<Row>, SparkSession, P> {

  public SparkMMapper(String id, String name, P props) {
    super(id, name, props);
  }
}
