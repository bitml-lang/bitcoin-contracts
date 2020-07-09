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

    // Create an istance of DbManager to store the data
    val manager = DbManager()

    // Add the serialized transactions that we need
    manager.addTransaction("T", "02000000000101a79326e1480a66cbb5ac55caf7198cf8070f40de1f6b96c09cf029ffeff4433c0000000000ffffffff01c09ee605000000002200203a5fcea85c2dca4480bd79f80bff55356b4779c6b5ef870a6c8086c1285e932f0247304402200b6ed5a7d2bdaee33ff597ba785fd4b5609ef2076595c40f9ac7f5e0fb202e00022068d3f5790252733f2312773a8405c7d192e6739d900285fca6dd5168071587cb012103fd3c8b7437f9c8b447a3d04aca9ffa04c430c324a49495f13d116395029aa93a00000000")
    manager.addTransaction("T1", "020000000001010508772d449eb3ccec3c9078d9be0afd4cb8f4dbdcc01fd4302cfca27c70f1a00000000000ffffffff01805cd70500000000160014d5ee2547793a01cd846e1347ba907821493587df0300483045022100a62117a69b7f64737f05cb637f38bf34add33affe0d197521c42301529bab70502207cde0fd9edeb6100f82f392a63f579a4f77ef48e4a367f61257211564c985b8a01475221028c96545ee165f631de2889ac3dd21bdf96efc7b9b92accc36c2107460c72ced721032ad0edc9ca87bc02f8ca5acb209d47913fa6a7d45133b3d4a16354a75421e32e52ae00000000")

    // Public keys
    val a_pub = "03fd3c8b7437f9c8b447a3d04aca9ffa04c430c324a49495f13d116395029aa93a"
    val b_pub = "028c96545ee165f631de2889ac3dd21bdf96efc7b9b92accc36c2107460c72ced7"
    val o_pub = "032ad0edc9ca87bc02f8ca5acb209d47913fa6a7d45133b3d4a16354a75421e32e"

    // Add the partecipants
    manager.addPartecipant("Alice", Seq(a_pub), "127.0.0.1", 25000)
    manager.addPartecipant("Bob", Seq(b_pub), "127.0.0.1", 25001)
    manager.addPartecipant("Oracle", Seq(o_pub), "127.0.0.1", 25002)


    // Creating chunks and entries for transactions
    val t_chunks = Seq(manager.createChunk(a_pub, 0))

    val t1_chunks = Seq(
      manager.createChunk(b_pub, 0, ChunkType.SIG_P2WSH),
      manager.createChunk(o_pub, 1, ChunkType.SIG_P2WSH, chunkPrivacy = ChunkPrivacy.AUTH)
    )

    // Linking amounts to entries
    val t_entry = manager.createEntry(1 btc, t_chunks)
    val t1_entry = manager.createEntry(0.99 btc, t1_chunks)

    // Add the metadata of the transactions
    manager.addMeta("T", Seq(t_entry))
    manager.addMeta("T1", Seq(t1_entry))

    // Get the initial state and return it
    manager getState
  }
}
