package Tests_Decentralized

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

class Test_Arbiter extends AnyFunSuite with BeforeAndAfterAll {
  private var testSystem: ActorSystem = _

  override def beforeAll(): Unit = {
    testSystem = ActorSystem(name = "internalTestSystem3")
    super.beforeAll()
  }

  override def afterAll(): Unit = {
    CoordinatedShutdown(testSystem).run(CoordinatedShutdown.unknownReason)
    Thread.sleep(500)
    super.afterAll()
  }

  test("Arbiter") {
    // get the initial state from the setup function
    val initialState = Setup.setup()
    val stateJson = new Serializer().prettyPrintState(initialState)

    // creating akka actor system
    val arbiter = testSystem.actorOf(Props(classOf[Client]), name="Arbiter")

    // private and public key
    val c_priv = PrivateKey.fromBase58("cQAEMfAQwbVDSUDT3snYu9QVfbdBTVMrm36zoArizBkAaPYTtLdH", Base58.Prefix.SecretKeyTestnet)._1
    val c_pub = PublicKey(ByteVector.fromValidHex("032ad0edc9ca87bc02f8ca5acb209d47913fa6a7d45133b3d4a16354a75421e32e"))

    // fetch the partecipant db from the initial state
    val arbiter_p = initialState.partdb.fetch("032ad0edc9ca87bc02f8ca5acb209d47913fa6a7d45133b3d4a16354a75421e32e").get

    // Initialize Alice with the state information.
    arbiter ! Init(c_priv, stateJson)


    // Start network interface.
    println(arbiter_p.endpoint.system)
    arbiter ! Listen("test_application_o.conf", arbiter_p.endpoint.system)

    // we declare an arbitrary timeout
    implicit val timeout : Timeout = Timeout(2000 milliseconds)

    while(true) {
      // simulation of the oracle checks to decide wether giving signature or not
      if(false) {
        print("Giving the signature to Bob")
        arbiter ! Authorize("T1_C_bob")
      } else if(false) {
        println("Giving the signature to Alice")
        arbiter ! Authorize("T1_C_alice")
      } else {
        println("Issuing a partial refund")
        arbiter ! Authorize("T1_C_split_alice")
        arbiter ! Authorize("T1_C_split_bob")
      }
      Thread.sleep(5000)
    }

    // final partecipant shutdown
    arbiter ! StopListening()
    CoordinatedShutdown(testSystem).run(CoordinatedShutdown.unknownReason)
    Thread.sleep(500)
  }
}
