package Escrow

import Managers.{ClientManager, Helpers}
import Tests_Decentralized.Setup
import akka.actor.{ActorSystem, CoordinatedShutdown}
import akka.util.Timeout
import fr.acinq.bitcoin.Crypto.{PrivateKey, PublicKey}
import fr.acinq.bitcoin._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite
import scodec.bits.ByteVector
import xyz.bitml.api.messaging.{Authorize, Init, Listen, StopListening}
import xyz.bitml.api.serialization.Serializer

import scala.concurrent.duration._

class Test_Arbiter extends AnyFunSuite with BeforeAndAfterAll {
  private var testSystem: ActorSystem = _
  val GUARD = 2

  override def beforeAll(): Unit = {
    testSystem = ActorSystem(name = "internalTestSystemArbiter")
    super.beforeAll()
  }

  override def afterAll(): Unit = {
    CoordinatedShutdown(testSystem).run(CoordinatedShutdown.unknownReason)
    Thread.sleep(500)
    super.afterAll()
  }

  test("Arbiter") {
    val log = Helpers.CustomLogger("Arbiter")

    // get the initial state from the setup function
    log.printStatus("Contract started - Executing Setup")
    val initialState = Setup.setup()
    val stateJson = new Serializer().prettyPrintState(initialState)

    // Declare client manager
    val cm = ClientManager()

    // creating akka actor system
    log.printStatus("Actor System Creation!")
    val contract = cm.createActor(testSystem, "Arbiter")

    // private and public key
    val c_priv = PrivateKey.fromBase58("cQAEMfAQwbVDSUDT3snYu9QVfbdBTVMrm36zoArizBkAaPYTtLdH", Base58.Prefix.SecretKeyTestnet)._1
    val c_pub = PublicKey(ByteVector.fromValidHex("032ad0edc9ca87bc02f8ca5acb209d47913fa6a7d45133b3d4a16354a75421e32e"))

    // fetch the partecipant db from the initial state
    log.printStatus("Fetching contract partecipants from Setup")
    val arbiter_p = initialState.partdb.fetch(c_pub.toString()).get

    // Initialize Alice with the state information.
    log.printStatus("Contract initiated with private key and initial state")
    contract ! Init(c_priv, stateJson)

    // Start network interface.
    log.printStatus("Starting newtork interface - Listening incoming traffic on port "+ arbiter_p.endpoint.port.get)
    contract ! Listen("test_application_o.conf", arbiter_p.endpoint.system)

    // we declare an arbitrary timeout
    implicit val timeout : Timeout = Timeout(2000 milliseconds)

    // simulation of the Arbiter checks to decide which partecipants gets the signature
    log.printStatus("Checking which transaction should be authorized")
    GUARD match {
      case 0 => contract ! Authorize("T1_C_bob"); log.printStatus("Authorizing T1_C_bob")
      case 1 => contract ! Authorize("T1_C_alice"); log.printStatus("Authorizing T1_C_alice")
      case 2 => contract ! Authorize("T1_C_split_alice"); log.printStatus("Authorizing T1_C_split_alice")
                contract ! Authorize("T1_C_split_bob"); log.printStatus("Authorizing T1_C_split_bob")
      case whoa => println("Unexpected control value "+ whoa.toString)
    }

    log.printStatus("Waiting 20 seconds before shut down")
    Thread.sleep(20000)

    // final partecipant shutdown
    log.printStatus("Shutting Down")
    contract ! StopListening()
    cm.shutSystem(testSystem)
  }
}
