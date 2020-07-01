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
import xyz.bitml.api.messaging.{AskForSigs, AssembledTx, CurrentState, DumpState, Init, Listen, SearchTx, StopListening, TryAssemble}
import xyz.bitml.api.persistence.{MetaStorage, ParticipantStorage, State, TxStorage}
import xyz.bitml.api.serialization.Serializer

import scala.concurrent.duration._
import scala.concurrent.Await

class Test_Alice extends AnyFunSuite with BeforeAndAfterAll {

  private var testSystem: ActorSystem = _

  override def beforeAll(): Unit = {
    testSystem = ActorSystem(name = "internalTestSystem1")
    super.beforeAll()
  }

  override def afterAll(): Unit = {
    CoordinatedShutdown(testSystem).run(CoordinatedShutdown.unknownReason)
    Thread.sleep(500)
    super.afterAll()
  }

  test("Alice") {
    val initialState = Setup.setup()
    val stateJson = new Serializer().prettyPrintState(initialState)
    //creating akka actors
    val alice = testSystem.actorOf(Props(classOf[Client]), name="Alice")

    val a_priv = PrivateKey.fromBase58("cVbFzgZSpnuKvNT5Z3DofF9dV4Dr1zFQJw9apGZDVaG73ULqM7XS", Base58.Prefix.SecretKeyTestnet)._1
    val a_pub = PublicKey(ByteVector.fromValidHex("03fd3c8b7437f9c8b447a3d04aca9ffa04c430c324a49495f13d116395029aa93a"))

    val alice_p = initialState.partdb.fetch("03fd3c8b7437f9c8b447a3d04aca9ffa04c430c324a49495f13d116395029aa93a").get
    // Initialize Alice with the state information.
    alice ! Init(a_priv, stateJson)
    // Start network interface.
    alice ! Listen("test_application.conf", alice_p.endpoint.system)

    //we declare an arbitrary timeout
    implicit val timeout : Timeout = Timeout(2000 milliseconds)

    //alice tries to assemble T and also publish it in the testnet
    val future2 = alice ? TryAssemble("T", autoPublish = false)
    //val tx = alice ! SearchTx("T")
    //println(tx)
    val res2 = Await.result(future2, timeout.duration).asInstanceOf[AssembledTx].serializedTx
    val tx = Transaction.read(res2)
    println(tx)
    // final partecipant shutdown
    alice ! StopListening()
    CoordinatedShutdown(testSystem).run(CoordinatedShutdown.unknownReason)
    Thread.sleep(500)
  }
}
