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

class Test_Alice extends AnyFunSuite with BeforeAndAfterAll {

  private var testSystem: ActorSystem = _
  val GUARD = false

  override def beforeAll(): Unit = {
    testSystem = ActorSystem(name = "internalTestSystemAlice")
    super.beforeAll()
  }

  override def afterAll(): Unit = {
    CoordinatedShutdown(testSystem).run(CoordinatedShutdown.unknownReason)
    Thread.sleep(500)
    super.afterAll()
  }

  test("Alice") {
    val log = Helpers.CustomLogger("Alice")

    //get the initial state from the setup function
    log.printStatus("Contract started - Executing Setup")
    val initialState = Setup.setup()
    val stateJson = new Serializer().prettyPrintState(initialState)

    // Declare the client manager
    val cm = ClientManager()

    //creating akka actor
    log.printStatus("Actor System Creation!")
    val contract = cm.createActor(testSystem, "Alice")

    //private and public key of the partecipant
    val a_priv = PrivateKey.fromBase58("cVbFzgZSpnuKvNT5Z3DofF9dV4Dr1zFQJw9apGZDVaG73ULqM7XS", Base58.Prefix.SecretKeyTestnet)._1
    //val a_pub = PublicKey(ByteVector.fromValidHex("03fd3c8b7437f9c8b447a3d04aca9ffa04c430c324a49495f13d116395029aa93a"))
    val a_pub = a_priv.publicKey

    //fetch the partecipant db from the initial state
    log.printStatus("Fetching contract partecipants from Setup")
    val alice_p = initialState.partdb.fetch(a_pub.toString()).get

    // Initialize Alice with the state information.
    log.printStatus("Contract initiated with private key and initial state")
    contract ! Init(a_priv, stateJson)

    // Start network interface.
    log.printStatus("Starting newtork interface - Listening incoming traffic on port "+ alice_p.endpoint.port.get)
    contract ! Listen("test_application.conf", alice_p.endpoint.system)

    //we declare an arbitrary timeout
    implicit val timeout : Timeout = Timeout(2000 milliseconds)

    var published = false
    var tries = 0

    //contract tries to assemble T and also publish it in the testnet
    log.printStatus("Trying to assemble and publish transaction T")
    var future = contract ? TryAssemble("T", autoPublish = true)
    var res = cm.getResult(future)
    if (!res.isEmpty()) log.printStatus("Assembled T with raw "+ res)
    published = cm.isPublished(res)
    if (published) log.printStatus("Publish successfull") else log.printStatus("Publish failed")
    published = false

    log.printStatus("Checking if Alice should authorize Bob to access signature for T1_bob")
    if(GUARD) {
      log.printStatus("Authorizing signature for T1_bob")
      contract ! Authorize("T1_bob")
    } else {
      log.printStatus("Signature for T1_bob not authorized")
    }

    while(!published && tries < 5) {
      log.printStatus("Try number: "+ tries)
      // Ask for signature
      log.printStatus("Asking signature for transaction T1_alice")
      contract ! AskForSigs("T1_alice")
      // Try to assemble the transaction and publish it
      log.printStatus("Trying to assemble and publish transaction T1_alice")
      future = contract ? TryAssemble("T1_alice", autoPublish = true)
      res = cm.getResult(future)
      if (!res.isEmpty()) log.printStatus("Assembled T1_alice with raw "+ res)
      published = cm.isPublished(res)
      if (published) log.printStatus("Publish successfull... Contract execution success!") else log.printStatus("Publish failed")

      if(!published) {
        log.printStatus("Asking signature for transaction T1_C_alice")
        contract ! AskForSigs("T1_C_alice")
        log.printStatus("Trying to assemble and publish transaction T1_C_alice")
        future = contract ? TryAssemble("T1_C_alice", autoPublish = true)
        res = cm.getResult(future)
        if (!res.isEmpty()) log.printStatus("Assembled T1_C_alice with raw "+ res)
        published = cm.isPublished(res)
        if (published) log.printStatus("Publish successfull... Contract execution success!") else log.printStatus("Publish failed")
      }

      if(!published) {
        log.printStatus("Asking signature for transaction T1_C_split_alice")
        contract ! AskForSigs("T1_C_split_alice")
        log.printStatus("Trying to assemble and publish transaction T1_C_split_alice")
        future = contract ? TryAssemble("T1_C_split_alice", autoPublish = true)
        res = cm.getResult(future)
        if (!res.isEmpty()) log.printStatus("Assembled T1_C_split_alice with raw "+ res)
        published = cm.isPublished(res)
        if (published) log.printStatus("Publish successfull... Contract execution success!") else log.printStatus("Publish failed")
      }
      tries+=1
      Thread.sleep(5000)
    }

    // final partecipant shutdown
    log.printStatus("Shutting Down")
    contract ! StopListening()
    cm.shutSystem(testSystem)
  }
}