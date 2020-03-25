/*
 * Copyright 2020 Azavea
 *
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

package geotrellis.server.ogc.conf

import com.azavea.maml.ast._
import geotrellis.raster.{RasterSource, ResampleMethod}
import geotrellis.raster.resample.NearestNeighbor
import geotrellis.server.ogc._
import geotrellis.store.GeoTrellisPath

// This sumtype corresponds to the in-config representation of a source
sealed trait OgcSourceConf {
  def name: String
  def styles: List[StyleConf]
  def resampleMethod: ResampleMethod
}

case class RasterSourceConf(
  name: String,
  title: String,
  source: String,
  defaultStyle: Option[String],
  styles: List[StyleConf],
  resampleMethod: ResampleMethod = NearestNeighbor
) extends OgcSourceConf {
  def toLayer: RasterOgcSource = {
    GeoTrellisPath.parseOption(source) match {
      case Some(_) => GeoTrellisOgcSource(
        name, title, source, defaultStyle, styles.map(_.toStyle), resampleMethod
      )
      case None => SimpleSource(
        name, title, RasterSource(source), defaultStyle, styles.map(_.toStyle), resampleMethod
      )
    }
  }
}

case class MapAlgebraSourceConf(
  name: String,
  title: String,
  algebra: Expression,
  defaultStyle: Option[String],
  styles: List[StyleConf],
  resampleMethod: ResampleMethod = NearestNeighbor
) extends OgcSourceConf {
  private def listParams(expr: Expression): List[String] = {
    def eval(subExpr: Expression): List[String] = subExpr match {
      case v: Variable =>
        List(v.name)
      case _ =>
        subExpr.children.flatMap(eval)
    }
    eval(expr)
  }

  /**
   * Given a list of all available `SimpleSourceConf` instances in the global [[Conf]] object,
   *  attempt to produce the parameter bindings necessary for evaluating the MAML [[Expression]]
   *  in the algebra field
   */
  def model(possibleSources: List[RasterOgcSource]): MapAlgebraSource = {
    val layerNames = listParams(algebra)
    val sourceList = layerNames.map { name =>
      val layerSrc = possibleSources.find(_.name == name).getOrElse {
        throw new Exception(
          s"MAML Layer expected but was unable to find the simple layer '$name', make sure all required layers are in the server configuration and are correctly spelled there and in all provided MAML")
      }
      name -> layerSrc.source
    }
    MapAlgebraSource(name, title, sourceList.toMap, algebra, defaultStyle, styles.map(_.toStyle), resampleMethod)
  }
}
