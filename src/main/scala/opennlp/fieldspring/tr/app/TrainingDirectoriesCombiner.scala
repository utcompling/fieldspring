package opennlp.fieldspring.tr.app

import java.io._

object TrainingDirectoriesCombiner extends App {
  val inDir1 = new File(args(0))
  val inDir2 = new File(args(1))
  val outDir = new File(args(2))

  if(!outDir.exists)
    outDir.mkdir

  // First clear the source directory:
  for(file <- outDir.listFiles)
    file.delete

  lineByLineCopy(inDir1, outDir)
  lineByLineCopy(inDir2, outDir)

  def lineByLineCopy(inDir:File, outDir:File) {
    for(file <- inDir.listFiles.filter(_.getName.endsWith(".txt"))) {
      val in = new BufferedReader(new FileReader(file))
      val out = new BufferedWriter(new FileWriter(outDir.getCanonicalPath+"/"+file.getName, true))
      println(inDir.getCanonicalPath+"/"+file.getName+" >> "+outDir.getCanonicalPath+"/"+file.getName)
      var line = "i"
      while(line != null) {
        try {
          line = in.readLine
        } catch {
          case e: java.nio.charset.MalformedInputException => line = "E"
        }
        if(line != null && line.size > 1)
          out.write(line+"\n")
      }
      out.close
      in.close
    }
  }
}
