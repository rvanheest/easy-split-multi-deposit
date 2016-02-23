package nl.knaw.dans.easy.multiDeposit.actions

import java.io.File

import nl.knaw.dans.easy.multiDeposit._
import nl.knaw.dans.easy.multiDeposit.actions.CreateSpringfieldAction._
import org.apache.commons.io.FileUtils

import scala.util.{Failure, Success}
import scala.xml.{XML, Utility}

class CreateSpringfieldActionsSpec extends UnitSpec {

  implicit val settings = Settings(
    mdDir = new File(testDir, "md"),
    springfieldInbox = new File(testDir, "springFieldInbox")
  )
  def testDataset = {
    val dataset = new Dataset
    dataset += "SF_DOMAIN" -> List("dans")
    dataset += "SF_USER" -> List("someDeveloper")
    dataset += "SF_COLLECTION" -> List("scala")
    dataset += "SF_PRESENTATION" -> List("unit-test")
    dataset += "FILE_AUDIO_VIDEO" -> List("yes")
    dataset += "FILE_SIP" -> List("videos/some.mpg")
    dataset += "FILE_SUBTITLES" -> List("videos/some.txt")
    dataset += "FILE_DATASET" -> List("footage/some.mpg")
  }
  def datasets(dataset: Dataset = testDataset) = {
    val datasets = new Datasets
    datasets += ("dataset-1" -> dataset)
  }

  "checkPreconditions" should "always succeed" in {
    CreateSpringfieldAction(1, datasets()).checkPreconditions shouldBe a[Success[_]]
  }

  "run" should "create a file when an empty ListBuffer was passed on" in {
    val datasets = new Datasets

    CreateSpringfieldAction(1, datasets).run shouldBe a[Success[_]]
    new File(settings.springfieldInbox, "springfield-actions.xml") should be a 'file
  }

  it should "fail with too little instructions" in {
    val datasets = new Datasets() += ("dataset-1" -> (testDataset -= "FILE_SIP"))
    val run = CreateSpringfieldAction(1, datasets).run()
    run shouldBe a[Failure[_]]

    (the [ActionException] thrownBy run.get).row shouldBe 1
    (the [ActionException] thrownBy run.get).message should
      include ("RuntimeException: Invalid video object")
  }

  it should "create the correct file with the correct input" in {
    CreateSpringfieldAction(1, datasets()).run shouldBe a[Success[_]]
    val generated = {
      val xmlFile = new File(settings.springfieldInbox, "springfield-actions.xml")
      xmlFile should be a 'file
      FileUtils.readFileToString(xmlFile)
    }

    generated should include ("subtitles=\"videos/some.txt\"")
    generated should include ("src=\"videos/some.mpg\"")
    generated should include ("target=\"/domain/dans/user/someDeveloper/collection/scala/presentation/unit-test\"")
  }

  "rollback" should "always succeed" in {
    CreateSpringfieldAction(1, datasets()).rollback shouldBe a[Success[_]]
  }

  "writeSpringfieldXml" should "create the correct file with the correct input" in {
    writeSpringfieldXml(1, datasets()) shouldBe a[Success[_]]
    val generated = {
      val xmlFile = new File(settings.springfieldInbox, "springfield-actions.xml")
      xmlFile should be a 'file
      FileUtils.readFileToString(xmlFile)
    }

    generated should include ("subtitles=\"videos/some.txt\"")
    generated should include ("src=\"videos/some.mpg\"")
    generated should include ("target=\"/domain/dans/user/someDeveloper/collection/scala/presentation/unit-test\"")
  }

  "toXML" should "return an empty xml when given empty datasets" in {
    Utility.trim(XML.loadString(toXML(new Datasets))) shouldBe
      Utility.trim(<actions></actions>)
  }

  it should "return the xml" in {
    Utility.trim(XML.loadString(toXML(datasets()))) shouldBe
      Utility.trim(<actions>
        <add target="/domain/dans/user/someDeveloper/collection/scala/presentation/unit-test">
          <video src="videos/some.mpg" target="0" subtitles="videos/some.txt"/>
        </add>
      </actions>)
  }

  "createAddElement" should "return an empy xml when given an empty list of videos" in {
    createAddElement("target value", Nil) shouldBe
      Utility.trim(<add target="target value"></add>)
  }

  it should "throw a RuntimeException when an incorrect video is supplied" in {
    val videos = List(
      Video("0", Some("videos/some0.mpg"), Some("videos/some0.txt")),
      Video("1", None, None),
      Video("2", Some("videos/some2.mpg"), Some("videos/some2.txt"))
    )

    (the [RuntimeException] thrownBy createAddElement("target value", videos)).getMessage should
      include ("Invalid video object:")
  }

  it should "return the xml" in {
    val videos = List(
      Video("0", Some("videos/some0.mpg"), Some("videos/some0.txt")),
      Video("1", Some("videos/some1.mpg"), None),
      Video("2", Some("videos/some2.mpg"), Some("videos/some2.txt"))
    )

    createAddElement("target value", videos) shouldBe
      Utility.trim(<add target="target value">
        <video src="videos/some0.mpg" target="0" subtitles="videos/some0.txt"/>
        <video src="videos/some1.mpg" target="1"/>
        <video src="videos/some2.mpg" target="2" subtitles="videos/some2.txt"/>
      </add>)
  }

  "extractVideos" should "return an empty map when given an empty dataset" in {
    extractVideos(new Dataset) shouldBe Map[String, List[Video]]()
  }

  it should "return an empty map when the FILE_AUDIO_VIDEO field is blank" in {
    extractVideos(testDataset += ("FILE_AUDIO_VIDEO" -> List(""))) shouldBe Map[String, List[Video]]()
  }

  it should "return an empty map when the FILE_AUDIO_VIDEO field is not yes" in {
    extractVideos(testDataset += ("FILE_AUDIO_VIDEO" -> List("nope!"))) shouldBe Map[String, List[Video]]()
  }

  it should "return a filled map when given the correct input with a single row in the dataset" in {
    extractVideos(testDataset) shouldBe
      Map("/domain/dans/user/someDeveloper/collection/scala/presentation/unit-test" ->
        List(Video("0", Some("videos/some.mpg"), Some("videos/some.txt"))))
  }

  it should "return a filled map when given the correct input with multiple rows in the dataset" +
    "and only the first row filled" in {
    def testDataset2 = {
      val dataset = new Dataset
      dataset += "SF_DOMAIN" -> List("dans", "")
      dataset += "SF_USER" -> List("someDeveloper", "")
      dataset += "SF_COLLECTION" -> List("scala", "")
      dataset += "SF_PRESENTATION" -> List("unit-test", "")
      dataset += "FILE_AUDIO_VIDEO" -> List("yes", "")
      dataset += "FILE_SIP" -> List("videos/some.mpg", "")
      dataset += "FILE_SUBTITLES" -> List("videos/some.txt", "")
      dataset += "FILE_DATASET" -> List("footage/some.mpg", "")
    }

    extractVideos(testDataset2) shouldBe
      Map("/domain/dans/user/someDeveloper/collection/scala/presentation/unit-test" ->
        List(Video("0", Some("videos/some.mpg"), Some("videos/some.txt"))))
  }

  it should "return a filled map when given the correct input with multiple rows in the dataset" in {
    def testDataset3 = {
      val dataset = new Dataset
      dataset += "SF_DOMAIN" -> List("dans", "")
      dataset += "SF_USER" -> List("someDeveloper", "")
      dataset += "SF_COLLECTION" -> List("scala", "")
      dataset += "SF_PRESENTATION" -> List("unit-test", "")
      dataset += "FILE_AUDIO_VIDEO" -> List("yes", "yes")
      dataset += "FILE_SIP" -> List("videos/some.mpg", "audio/herrie.mp3")
      dataset += "FILE_SUBTITLES" -> List("videos/some.txt", "")
      dataset += "FILE_DATASET" -> List("footage/some.mpg", "")
    }

    extractVideos(testDataset3) shouldBe
      Map("/domain/dans/user/someDeveloper/collection/scala/presentation/unit-test" ->
        List(Video("0", Some("videos/some.mpg"), Some("videos/some.txt")),
          Video("1", Some("audio/herrie.mp3"), None)))
  }

  "getSpringfieldPath" should "not yield a path when given an empty dataset" in {
    getSpringfieldPath(new Dataset, 0) shouldBe None
  }

  it should "not yield a path when no domain is specified in the dataset" in {
    getSpringfieldPath(testDataset -= "SF_DOMAIN", 0) shouldBe None
  }

  it should "not yield a path when no user is specified in the dataset" in {
    getSpringfieldPath(testDataset -= "SF_DOMAIN", 0) shouldBe None
  }

  it should "not yield a path when no collection is specified in the dataset" in {
    getSpringfieldPath(testDataset -= "SF_COLLECTION", 0) shouldBe None
  }

  it should "not yield a path when no presentation is specified in the dataset" in {
    getSpringfieldPath(testDataset -= "SF_PRESENTATION", 0) shouldBe None
  }

  it should "not yield a path when given the incorrect index" in {
    getSpringfieldPath(testDataset, 1) shouldBe None
  }

  it should "yield the path when given a correct dataset" in {
    getSpringfieldPath(testDataset, 0) shouldBe
      Some("/domain/dans/user/someDeveloper/collection/scala/presentation/unit-test")
  }
}