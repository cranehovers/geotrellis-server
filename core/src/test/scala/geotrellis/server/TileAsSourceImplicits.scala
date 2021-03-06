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

package geotrellis.server

import geotrellis.raster._
import geotrellis.proj4._
import geotrellis.layer._
import geotrellis.vector.Extent

import cats.effect._
import cats.data.{NonEmptyList => NEL}

// The entire point of this is to provide a *very unsafe* way to quickly test MAML evaluation
// ZXY/Extent/CellSize/Etc are just ignored and the tile you pass in is what will be used
trait TileAsSourceImplicits {
  val tmsLevels: Array[LayoutDefinition] = {
    val scheme = ZoomedLayoutScheme(WebMercator, 256)
    for (zoom <- 0 to 64) yield scheme.levelForZoom(zoom).layout
  }.toArray

  implicit val extentReification: ExtentReification[IO, Tile] = { self => (extent: Extent, _: Option[CellSize]) =>
    IO.pure(ProjectedRaster(MultibandTile(self), extent, WebMercator))
  }

  implicit val nodeRasterExtents: HasRasterExtents[IO, Tile] = { self =>
    IO.pure(NEL.of(RasterExtent(Extent(0, 0, 100, 100), 1.0, 1.0, self.cols, self.rows)))
  }

  implicit val tmsReification: TmsReification[IO, Tile] = { (self, buffer) => (z: Int, x: Int, y: Int) =>
    {
      val extent = tmsLevels(z).mapTransform.keyToExtent(x, y)
      IO.pure(ProjectedRaster(MultibandTile(self), extent, WebMercator))
    }
  }
}
