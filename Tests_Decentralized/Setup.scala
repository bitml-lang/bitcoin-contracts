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

object Setup {
  def setup(): State = {
    // Declare the serialized transactions that we need
    val t = Transaction.read("02000000000101a79326e1480a66cbb5ac55caf7198cf8070f40de1f6b96c09cf029ffeff4433c0000000000ffffffff01c09ee605000000002200203a5fcea85c2dca4480bd79f80bff55356b4779c6b5ef870a6c8086c1285e932f0247304402200b6ed5a7d2bdaee33ff597ba785fd4b5609ef2076595c40f9ac7f5e0fb202e00022068d3f5790252733f2312773a8405c7d192e6739d900285fca6dd5168071587cb012103fd3c8b7437f9c8b447a3d04aca9ffa04c430c324a49495f13d116395029aa93a00000000")
    val t1 = Transaction.read("020000000001010508772d449eb3ccec3c9078d9be0afd4cb8f4dbdcc01fd4302cfca27c70f1a00000000000ffffffff01805cd70500000000160014d5ee2547793a01cd846e1347ba907821493587df0300483045022100a62117a69b7f64737f05cb637f38bf34add33affe0d197521c42301529bab70502207cde0fd9edeb6100f82f392a63f579a4f77ef48e4a367f61257211564c985b8a01475221028c96545ee165f631de2889ac3dd21bdf96efc7b9b92accc36c2107460c72ced721032ad0edc9ca87bc02f8ca5acb209d47913fa6a7d45133b3d4a16354a75421e32e52ae00000000")

    // Save the transactions
    val dbx = new TxStorage()
    dbx.save("T", t)
    dbx.save("T1", t1)

    // Public keys
    val a_pub = PublicKey(ByteVector.fromValidHex("03fd3c8b7437f9c8b447a3d04aca9ffa04c430c324a49495f13d116395029aa93a"))
    val b_pub = PublicKey(ByteVector.fromValidHex("028c96545ee165f631de2889ac3dd21bdf96efc7b9b92accc36c2107460c72ced7"))
    val o_pub = PublicKey(ByteVector.fromValidHex("032ad0edc9ca87bc02f8ca5acb209d47913fa6a7d45133b3d4a16354a75421e32e"))

    // Declare the partecipants along with their name, public keys and endpoints
    val alice_p = Participant("Alice", List(a_pub), Address("akka", "test", "127.0.0.1", 25000))
    val bob_p = Participant("Bob", List(b_pub), Address("akka", "test", "127.0.0.1", 25001))
    val oracle_p = Participant("Oracle", List(o_pub), Address("akka", "test", "127.0.0.1", 25002))

    // Saving the partecipants on a storage
    val partDb = new ParticipantStorage()
    partDb.save(alice_p)
    partDb.save(bob_p)
    partDb.save(oracle_p)

    // Creating chunks and entries for transactions
    val t_chunks = Seq(
      ChunkEntry(ChunkType.SIG_P2WPKH, ChunkPrivacy.PUBLIC, 0, Option(a_pub), ByteVector.empty))
    val t1_chunks = Seq(
      ChunkEntry(ChunkType.SIG_P2WSH, ChunkPrivacy.PUBLIC, 0, Option(b_pub), ByteVector.empty),
      ChunkEntry(ChunkType.SIG_P2WSH, ChunkPrivacy.AUTH, 1, Option(o_pub), ByteVector.empty))

    val t_entry = TxEntry(name = "T", indexData = Map(0 -> IndexEntry(amt = Btc(1).toSatoshi ,chunkData = t_chunks)))
    val t1_entry = TxEntry(name = "T1", indexData = Map(0 -> IndexEntry(amt = Btc(0.99).toSatoshi ,chunkData = t1_chunks)))

    // Save the transactions meta on a storage
    val metadb = new MetaStorage()
    metadb.save(t_entry)
    metadb.save(t1_entry)

    // Make the initial state and return it
    val initialState = State(partDb, dbx, metadb)
    initialState
  }
}
