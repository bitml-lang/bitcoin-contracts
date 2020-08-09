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
import xyz.bitml.api.messaging.{AskForSigs, AssembledTx, CurrentState, DumpState, Init, Listen, SearchTx, StopListening, TryAssemble}
import xyz.bitml.api.persistence.{MetaStorage, ParticipantStorage, State, TxStorage}
import xyz.bitml.api.serialization.Serializer

import scala.concurrent.duration._
import scala.concurrent.Await

class Test_Bob  extends AnyFunSuite with BeforeAndAfterAll  {
  private var testSystem: ActorSystem = _

  override def beforeAll(): Unit = {
    testSystem = ActorSystem(name = "internalTestSystemBob")
    super.beforeAll()
  }

  override def afterAll(): Unit = {
    CoordinatedShutdown(testSystem).run(CoordinatedShutdown.unknownReason)
    Thread.sleep(500)
    super.afterAll()
  }

  test("Bob") {
    val log = Helpers.CustomLogger("Bob")

    log.printStatus("Contract started - Executing Setup")
    // get the initial state from the setup function
    val initialState = Setup.setup()
    val stateJson = new Serializer().prettyPrintState(initialState)

    // Declare client manager
    val cm = ClientManager()

    // Create actor
    log.printStatus("Actor System Creation!")
    val contract = cm.createActor(testSystem, "Bob")

    // private and public key
    val b_priv = PrivateKey.fromBase58("cPU3AmQFsBxvrBgTWc1j3pS6T7m4bYWMFQyPnR9Qp3o3UTCBwspZ", Base58.Prefix.SecretKeyTestnet)._1
    val b_pub = PublicKey(ByteVector.fromValidHex("028c96545ee165f631de2889ac3dd21bdf96efc7b9b92accc36c2107460c72ced7"))

    // fetch the partecipant db from the initial state
    val bob_p = initialState.partdb.fetch(b_pub.toString()).get

    // Initialize Alice with the state information.
    log.printStatus("Contract initiated with private key and initial state")
    contract ! Init(b_priv, stateJson)

    // Start network interface.
    log.printStatus("Starting newtork interface - Listening incoming traffic on port "+ bob_p.endpoint.port.get)
    contract ! Listen("test_application_b.conf", bob_p.endpoint.system)

    // we declare an arbitrary timeout
    implicit val timeout: Timeout = Timeout(2000 milliseconds)

    log.printStatus("Trying to assemble and publish transaction T_timeout")
    val future = contract ? TryAssemble("bob_T_timeout", autoPublish = true)

    //alice has produced a transaction
    val res = cm.getResult(future)
    log.printStatus("Assembled T_timeout with raw " + res)

    if (cm.isPublished(res)) {
      log.printStatus("Publish successfull... Contract executed succesfully")
    } else {
      log.printStatus("Publish failed... Checking if t_reveal was published")
      val future = contract ? SearchTx("alice_T_reveal")
      val res = cm.getResult(future)
      if(res.isEmpty) log.printStatus("T_reveal was not published! Contract failed!") else log.printStatus("T_reveal was published! Contract executed succesfully!")
    }

    log.printStatus("Shutting Down")
    contract ! StopListening()
    cm.shutSystem(testSystem)
  }

}
