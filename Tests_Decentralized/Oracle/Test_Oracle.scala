package Tests_Decentralized

import Managers.{ClientManager, Helpers}
import akka.actor.{ActorSystem, Address, CoordinatedShutdown, Props}
import akka.util.Timeout
import fr.acinq.bitcoin.Crypto.{PrivateKey, PublicKey}
import fr.acinq.bitcoin._
import akka.pattern.ask
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite
import scodec.bits.ByteVector
import wf.bitcoin.javabitcoindrpcclient.BitcoinJSONRPCClient
import xyz.bitml.api.{ChunkEntry, ChunkPrivacy, ChunkType, Client, IndexEntry, Participant, TxEntry}
import xyz.bitml.api.messaging.{AskForSigs, AssembledTx, Authorize, CurrentState, DumpState, Init, Listen, SearchTx, StopListening, TryAssemble}
import xyz.bitml.api.persistence.{MetaStorage, ParticipantStorage, State, TxStorage}
import xyz.bitml.api.serialization.Serializer

import scala.concurrent.duration._
import scala.concurrent.Await
import scala.io.StdIn

class Test_Oracle extends AnyFunSuite with BeforeAndAfterAll {
  private var testSystem: ActorSystem = _
  val GUARD = true

  override def beforeAll(): Unit = {
    testSystem = ActorSystem(name = "internalTestSystem3")
    super.beforeAll()
  }

  override def afterAll(): Unit = {
    CoordinatedShutdown(testSystem).run(CoordinatedShutdown.unknownReason)
    Thread.sleep(500)
    super.afterAll()
  }

  test("Oracle") {
    val log = Helpers.CustomLogger("Oracle")

    // get the initial state from the setup function
    log.printStatus("Contract started - Executing Setup")
    val initialState = Setup.setup()
    val stateJson = new Serializer().prettyPrintState(initialState)

    // Declare client manager
    val cm = ClientManager()

    // Create actor
    log.printStatus("Actor System Creation!")
    val contract = cm.createActor(testSystem, "Oracle")

    // private and public key
    val o_priv = PrivateKey.fromBase58("cQAEMfAQwbVDSUDT3snYu9QVfbdBTVMrm36zoArizBkAaPYTtLdH", Base58.Prefix.SecretKeyTestnet)._1
    val o_pub = PublicKey(ByteVector.fromValidHex("032ad0edc9ca87bc02f8ca5acb209d47913fa6a7d45133b3d4a16354a75421e32e"))

    // fetch the partecipant db from the initial state
    log.printStatus("Fetching contract partecipants from Setup")
    val oracle_p = initialState.partdb.fetch(o_pub.toString()).get

    // Initialize Alice with the state information.
    log.printStatus("Contract initiated with private key and initial state")
    contract ! Init(o_priv, stateJson)


    // Start network interface.
    log.printStatus("Starting newtork interface - Listening incoming traffic on port "+ oracle_p.endpoint.port.get)
    contract ! Listen("test_application_o.conf", oracle_p.endpoint.system)

    // simulation of the oracle checks to decide wether giving signature or not
    log.printStatus("Check if the guard allows Bob to get the signature")
    if(GUARD) {
      log.printStatus("Unlocking the signature for Bob on T1")
      contract ! Authorize("T1")
    } else {
      log.printStatus("Signature for Bob on T1 not authorized")
    }

    log.printStatus("Waiting 20 seconds before shutting down")
    Thread.sleep(20000)

    // final partecipant shutdown
    log.printStatus("Shutting Down")
    contract ! StopListening()
    cm.shutSystem(testSystem)
  }
}
