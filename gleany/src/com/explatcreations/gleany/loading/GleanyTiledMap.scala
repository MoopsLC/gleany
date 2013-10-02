/******************************************************************************
 * Copyright 2013, deweyvm
 *
 * This file is part of Gleany.
 *
 * Gleany is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 *
 * Gleany is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along
 * with Gleany.
 *
 * If not, see <http://www.gnu.org/licenses/>.
 *****************************************************************************/

package com.explatcreations.gleany.loading

import com.badlogic.gdx.utils.XmlReader
import com.explatcreations.gleany.Glean

trait ITiledMap {
    val width:Int
    val height:Int
    def getProperty(name:String):String
    def getObjectLayer(name:String):Option[Seq[MapObject]]
    def getTileLayer(name:String):Array[Array[Int]]
}


class GleanyTiledMap(mapName:String) extends ITiledMap {
    type TileData = (Int, Int, Array[Array[Int]])
    private val xml = new XmlReader()
    private val root = xml.parse(Glean.y.files.map(mapName))
    private val gidMap = makeGidMap
    private val layers = makeLayers
    private val objects = makeObjects
    private val properties = makeProperties
    override val width = getWidth
    override val height = getHeight

    override def getProperty(name:String):String = {
        properties(name)
    }

    override def getObjectLayer(name:String):Option[Seq[MapObject]] = {
        objects.get(name)
    }

    override def getTileLayer(name:String):Array[Array[Int]] = {
        layers(name)._3
    }

    private def getFromLayer(getter:TileData => Int) = {
        if (layers.isEmpty) {
            throw new IllFormedMapException(mapName, "no tile layers found")
        } else {
            getter(layers.head._2)
        }
    }

    private def getWidth = {
        getFromLayer(_._1)
    }

    private def getHeight = {
        getFromLayer(_._2)
    }

    private def makeGidMap:Map[String, Int] = {
        val tilesetNodes = root.getChildrenByName("tileset")
        val pairs = tilesetNodes.toArray map { e:XmlReader.Element =>
            val name = e.get("name")
            val firstGid = e.getInt("firstgid")
            (name, firstGid)
        }
        pairs.toMap
    }

    private def makeObjects:Map[String, Seq[MapObject]] = {
        val groupsNodes = root.getChildrenByName("objectgroup")
        val objectPairs = groupsNodes.toArray map { e:XmlReader.Element =>
            val name = e.get("name")
            val objectsNodes = e.getChildrenByName("object")
            val objects = objectsNodes.toArray map { obj:XmlReader.Element =>
                val x = obj.getInt("x")
                val y = obj.getInt("y")
                val `type` = obj.get("type")
                //val width = obj.getInt("width")//width ignored
                //val height = obj.getInt("height")//height ignored
                val propertiesNode:XmlReader.Element = obj.getChildByName("properties")
                val propertyNodes = propertiesNode.getChildrenByName("property")
                val allProperties = (propertyNodes.toArray map { prop:XmlReader.Element =>
                    val name = prop.get("name")
                    val value = prop.get("value")
                    (name, value)
                }).toMap

                MapObject(`type`, x, y, allProperties)
            } : Seq[MapObject]
            (name, objects)
        }
        objectPairs.toMap

    }

    private def makeLayers:Map[String,TileData] = {
        val layerNodes = root.getChildrenByName("layer")
        val pairs = layerNodes.toArray map { e:XmlReader.Element =>
            val name = e.get("name")
            val width = e.getInt("width")
            val height = e.getInt("height")
            val tiles: Array[Array[Int]] = parseCsv(gidMap(name), e.getChildByName("data").getText, width, height)
            (name, (width, height, tiles))
        }
        pairs.toMap
    }

    private def makeProperties = {
        val propertyElement = root.getChildByName("properties")
        val pairs = 0 until propertyElement.getChildCount map { j:Int =>
            val prop = propertyElement.getChild(j)
            val key = prop.get("name")
            val value = prop.get("value")
            (key, value)
        }
        pairs.toMap
    }

    private def parseCsv(firstGid:Int, data:String, width:Int, height:Int):Array[Array[Int]] = {
        val rows = data.split("\r\n|\r|\n")
        rows.map {str => str.replaceAll(",\\s*$", "").split(",\\s*").map(_.toInt - firstGid)}
    }

    override def toString = {
        val layerString = (layers map { case (name, tiles) =>
            "Tile Layer: " + name
        }).mkString("", "\n", "\n")

        def objToString(obj:MapObject) = {
            val propertiesString = (obj.properties map { case (key, value) =>
                "(%s -> %s)".format(key, value)
            }).mkString(",")
            "Object <type=%s> <x=%d> <y=%d> <properties=%s>".format(obj.`type`, obj.x, obj.y, propertiesString)
        }
        val objectsString = (objects map {case (name, objects) =>
            "Object Layer: " + name + "\n" +
                (objects map objToString).mkString("    ", "\n", "")
        }).mkString("\n")
        layerString + objectsString
    }
}


object IllFormedMapException {
    def getMessage(mapName:String, reason:String) = {
        "Failed to load map \"%s\": %s" format (mapName, reason)
    }
}
class IllFormedMapException(mapName:String, reason:String) extends RuntimeException(IllFormedMapException.getMessage(mapName, reason))

case class MapObject(`type`:String, x:Int, y:Int, properties:Map[String,String])

