package opennlp.fieldspring.tr.tpp

import opennlp.fieldspring.tr.util._
import opennlp.fieldspring.tr.topo._
import opennlp.fieldspring.tr.app._
import opennlp.fieldspring.tr.topo.gaz._
import opennlp.fieldspring.preprocess._

import java.io._
//import java.util._
import java.util.ArrayList
import java.util.zip._
import org.apache.commons.compress.compressors.bzip2._

import scala.collection.JavaConversions._

import org.clapper.argot._
import ArgotConverters._

object LinkTravelWriter extends App {

  val parser = new ArgotParser("fieldspring run opennlp.fieldspring.preprocess.LinkTravelWriter", preUsage = Some("Fieldspring"))

  //val gazInputFile = parser.option[String](List("g", "gaz"), "gaz", "serialized gazetteer input file")
  val articleNamesIDsCoordsFile = parser.option[String](List("a", "art"), "art", "wiki article IDs, titles, and coordinates (as output by ExtractGeotaggedListFromWikiDump)")
  val linkFileOption = parser.option[String](List("l", "links"), "links", "link data file (as output by ExtractLinksFromWikiDump)")
  val redirectsInputFile = parser.option[String](List("r", "redirects"), "redirects", "redirects input file")
  val dpcOption = parser.option[Int](List("d", "dpc"), "dpc", "degrees per cell")

  try {
    parser.parse(args)
  }
  catch {
    case e: ArgotUsageException => println(e.message); sys.exit(0)
  }

  val dpc = dpcOption.value.getOrElse(10)

  val redirectRE = """^(.+)\t(.+)$""".r

  val linkFile = linkFileOption.value.get
  //val gazPath = gazInputFile.value.get
  val articleInfoFile = articleNamesIDsCoordsFile.value.get
  val articleInfos = new scala.collection.mutable.HashMap[String, ArrayList[(Int, Coordinate)]]

  /*println("Reading serialized gazetteer from " + gazPath + " ...")
  val gis = new GZIPInputStream(new FileInputStream(gazPath))
  val ois = new ObjectInputStream(gis)
  val gnGaz = ois.readObject.asInstanceOf[GeoNamesGazetteer]
  gis.close*/

  val idsToCoords = new scala.collection.mutable.HashMap[Int, Coordinate]
  val rawNamesToIDs = new scala.collection.mutable.HashMap[String, Int]

  println("Reading article info from "+articleInfoFile+" ...")
  for(line <- scala.io.Source.fromFile(articleInfoFile).getLines) {
    val tokens = line.split("\t")
    val id = tokens(0).toInt
    val nameRaw = tokens(1)
    //val looseLookupName = ExtractLinksFromWikiDump.looseLookup(gnGaz, nameRaw)._1
    //if(looseLookupName != null)
    //  println(nameRaw + " --> " + looseLookupName)
    rawNamesToIDs.put(nameRaw, id)
    val name = nameRaw//if(looseLookupName != null && looseLookupName.trim.length > 0) looseLookupName.trim.toLowerCase else null
    //if(name != null) {
      val coordPair = tokens(2).split(",")
      val lat = coordPair(0).toDouble
      val lon = coordPair(1).toDouble
      val infos = articleInfos.getOrElse(name, new ArrayList[(Int, Coordinate)])
      val coord = Coordinate.fromDegrees(lat, lon)
      infos.add((id, coord))
      articleInfos.put(name, infos)
      idsToCoords.put(id, coord)
    //}
  }

  println("Reading redirects from " + redirectsInputFile.value.get + " ...")
  val redirects =
  (for(line <- scala.io.Source.fromFile(redirectsInputFile.value.get).getLines) yield {
    line match {
      case redirectRE(title1, title2) => { if(rawNamesToIDs.contains(title1) && rawNamesToIDs.contains(title2)) Some((rawNamesToIDs(title1), rawNamesToIDs(title2))) else None }
      case _ => None
    }
  }).flatten.toMap


  val links = new scala.collection.mutable.HashMap[Int, scala.collection.mutable.HashMap[Int, Int]] // location1.id => (location2.id => count)

  println("Reading links from "+linkFile+" ...")
  val in = new DataInputStream(new FileInputStream(linkFile))

  try {
    while(true) {
      val id1 = in.readInt
      val rawId2 = in.readInt
      val id2 = if(redirects.contains(rawId2)) redirects(rawId2) else rawId2 // handle redirects
      val count = in.readInt

      val destinations = links.getOrElse(id1, new scala.collection.mutable.HashMap[Int, Int])

      destinations.put(id2, count)
      links.put(id1, destinations)
    }
  } catch {
    case e:Exception => 
  }

  in.close

  val cellsToLinkCounts = new scala.collection.mutable.HashMap[Int, scala.collection.mutable.HashMap[Int, Int]] // market1 --> (market2, count)

  for((name, infos) <- articleInfos) {
    for((id, coord) <- infos) {
      val cellNum = TopoUtil.getCellNumber(coord, dpc)
      if(links.contains(id)) {
        val destinations = cellsToLinkCounts.getOrElse(cellNum, new scala.collection.mutable.HashMap[Int, Int])
        for((destination, count) <- links(id)) {
          if(idsToCoords.contains(destination)) {
            val destCellNum = TopoUtil.getCellNumber(idsToCoords(destination), dpc)
            val prevCount = destinations.getOrElse(destination, 0)
            destinations.put(destCellNum, prevCount + count)
          }
        }
        cellsToLinkCounts.put(cellNum, destinations)
      }
    }
  }

  val london = Coordinate.fromDegrees(51, 0.0)
  val newYork = Coordinate.fromDegrees(40.5, -74)
  val losAngeles = Coordinate.fromDegrees(34, -118)
  val sydney = Coordinate.fromDegrees(-34, 151)

  println("London: "+TopoUtil.getCellNumber(london, dpc))
  println("New York: "+TopoUtil.getCellNumber(newYork, dpc))
  println("Los Angeles: "+TopoUtil.getCellNumber(losAngeles, dpc))
  println("Sydney: "+TopoUtil.getCellNumber(sydney, dpc))

  for((id, destinations) <- cellsToLinkCounts) {
    println(id+" = "+TopoUtil.getCellCenter(id, dpc))
    val total = destinations.map(_._2).sum
    for((destination, count) <- destinations) {
      println(id+" "+destination+" ("+TopoUtil.getCellCenter(destination, dpc)+") "+count.toDouble/total+" ("+count+"/"+total+")")
    }
  }
}
