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

    // get the initial state from the setup function
    log.printStatus("Contract started - Executing Setup")
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
    log.printStatus("Fetching contract partecipants from Setup")
    val bob_p = initialState.partdb.fetch(b_pub.toString()).get

    // Initialize Alice with the state information.
    log.printStatus("Contract initiated with private key and initial state")
    contract ! Init(b_priv, stateJson)

    // Start network interface.
    log.printStatus("Starting newtork interface - Listening incoming traffic on port "+ bob_p.endpoint.port.get)
    contract ! Listen("test_application_b.conf", bob_p.endpoint.system)

    // we declare an arbitrary timeout
    implicit val timeout: Timeout = Timeout(2000 milliseconds)

    log.printStatus("Trying to assemble transaction fundsB")
    var future = contract ? TryAssemble("fundsB", autoPublish = true)
    var res = cm.getResult(future)
    log.printStatus("Assembled fundsB with raw " + res)
    var published = cm.isPublished(res)
    if (published) log.printStatus("Publish successfull.. Contract executed successfully") else log.printStatus("Publish failed.. retrying")
    published = false

    contract ! Authorize("Tinit")
    while(!published) {
      //alice tries to assemble T and also publish it in regtest
      log.printStatus("Trying to assemble transaction Tinit")
      future = contract ? TryAssemble("Tinit", autoPublish = true)
      res = cm.getResult(future)
      log.printStatus("Assembled Tinit with raw " + res)
      published = cm.isPublished(res)
      if (published) log.printStatus("Publish successfull.. Contract executed successfully") else log.printStatus("Publish failed.. retrying")
      Thread.sleep(4000)
      published = true
    }

    published = false
    contract ! Authorize("T1")
    contract ! Authorize("TwinA")
    while(!published) {
      log.printStatus("Trying to assemble transaction T1")
      contract ! AskForSigs("T1")
      future = contract ? TryAssemble("T1", autoPublish = true)
      res = cm.getResult(future)
      log.printStatus("Assembled T1 with raw " + res)
      published = cm.isPublished(res)
      if (published) log.printStatus("Publish successfull.. Contract executed successfully") else log.printStatus("Publish failed.. retrying")
      Thread.sleep(4000)
    }
/*
    log.printStatus("Trying to assemble transaction T")
    contract ! AskForSigs("TwinB")
    future = contract ? TryAssemble("TwinA", autoPublish = true)
    res = cm.getResult(future)
    log.printStatus("Assembled T with raw " + res)
    published = cm.isPublished(res)
    if (published) log.printStatus("Publish successfull.. Contract executed successfully") else log.printStatus("Publish failed.. retrying")

    contract ? Authorize("TwinA")

    log.printStatus("Trying to assemble transaction T")
    contract ! AskForSigs("Tsplit")
    future = contract ? TryAssemble("Tsplit", autoPublish = true)
    res = cm.getResult(future)
    log.printStatus("Assembled T with raw " + res)
    published = cm.isPublished(res)
    if (published) log.printStatus("Publish successfull.. Contract executed successfully") else log.printStatus("Publish failed.. retrying")
*/
    // final partecipant shutdown
    log.printStatus("Shutting Down")
    contract ! StopListening()
    cm.shutSystem(testSystem)
  }

}
