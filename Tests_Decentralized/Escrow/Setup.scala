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
    manager.addTransaction("T", "02000000000101a8a42cac4d259f4dc57b2ff68a5eca89f35dba928a5b10e9cba5f654f94f661d0100000000ffffffff01c09ee6050000000022002061380d8e7a652ddb421264cea9870ff21c444f0ba67cb8298c09a3cb3d04d23102473044022029c289ac5c3f83885ff2891d094a4954a596673a0629e5ae21c27e5b1b95f5a202207a981865d57d03a40b974cf62a80c9ca0edcf1ba83b9a4eda3ab4e3248d92b23012103fd3c8b7437f9c8b447a3d04aca9ffa04c430c324a49495f13d116395029aa93a00000000")
    manager.addTransaction("T1_bob", "020000000001017b54c5ad9dee275b388717824779c23829678e205923a95743f759834329f0ea0000000000ffffffff01805cd70500000000160014d5ee2547793a01cd846e1347ba907821493587df0400000069522103fd3c8b7437f9c8b447a3d04aca9ffa04c430c324a49495f13d116395029aa93a21028c96545ee165f631de2889ac3dd21bdf96efc7b9b92accc36c2107460c72ced721032ad0edc9ca87bc02f8ca5acb209d47913fa6a7d45133b3d4a16354a75421e32e53ae00000000")
    manager.addTransaction("T1_alice", "020000000001017b54c5ad9dee275b388717824779c23829678e205923a95743f759834329f0ea0000000000ffffffff01805cd705000000001600140b09d7a4d5ec473247d259d6e066b6596807619d0400000069522103fd3c8b7437f9c8b447a3d04aca9ffa04c430c324a49495f13d116395029aa93a21028c96545ee165f631de2889ac3dd21bdf96efc7b9b92accc36c2107460c72ced721032ad0edc9ca87bc02f8ca5acb209d47913fa6a7d45133b3d4a16354a75421e32e53ae00000000")
    manager.addTransaction("T1_C_bob", "020000000001017b54c5ad9dee275b388717824779c23829678e205923a95743f759834329f0ea0000000000ffffffff01805cd70500000000160014d5ee2547793a01cd846e1347ba907821493587df0400000069522103fd3c8b7437f9c8b447a3d04aca9ffa04c430c324a49495f13d116395029aa93a21028c96545ee165f631de2889ac3dd21bdf96efc7b9b92accc36c2107460c72ced721032ad0edc9ca87bc02f8ca5acb209d47913fa6a7d45133b3d4a16354a75421e32e53ae00000000")
    manager.addTransaction("T1_C_alice", "020000000001017b54c5ad9dee275b388717824779c23829678e205923a95743f759834329f0ea0000000000ffffffff01805cd705000000001600140b09d7a4d5ec473247d259d6e066b6596807619d0400000069522103fd3c8b7437f9c8b447a3d04aca9ffa04c430c324a49495f13d116395029aa93a21028c96545ee165f631de2889ac3dd21bdf96efc7b9b92accc36c2107460c72ced721032ad0edc9ca87bc02f8ca5acb209d47913fa6a7d45133b3d4a16354a75421e32e53ae00000000")
    manager.addTransaction("T1_C_split_alice", "020000000001017b54c5ad9dee275b388717824779c23829678e205923a95743f759834329f0ea0000000000ffffffff0240aeeb02000000001600140b09d7a4d5ec473247d259d6e066b6596807619d40aeeb0200000000160014d5ee2547793a01cd846e1347ba907821493587df0400000069522103fd3c8b7437f9c8b447a3d04aca9ffa04c430c324a49495f13d116395029aa93a21028c96545ee165f631de2889ac3dd21bdf96efc7b9b92accc36c2107460c72ced721032ad0edc9ca87bc02f8ca5acb209d47913fa6a7d45133b3d4a16354a75421e32e53ae00000000")
    manager.addTransaction("T1_C_split_bob", "020000000001017b54c5ad9dee275b388717824779c23829678e205923a95743f759834329f0ea0000000000ffffffff0240aeeb02000000001600140b09d7a4d5ec473247d259d6e066b6596807619d40aeeb0200000000160014d5ee2547793a01cd846e1347ba907821493587df0400000069522103fd3c8b7437f9c8b447a3d04aca9ffa04c430c324a49495f13d116395029aa93a21028c96545ee165f631de2889ac3dd21bdf96efc7b9b92accc36c2107460c72ced721032ad0edc9ca87bc02f8ca5acb209d47913fa6a7d45133b3d4a16354a75421e32e53ae00000000")

    // Public keys
    val a_pub = "03fd3c8b7437f9c8b447a3d04aca9ffa04c430c324a49495f13d116395029aa93a"
    val b_pub = "028c96545ee165f631de2889ac3dd21bdf96efc7b9b92accc36c2107460c72ced7"
    val c_pub = "032ad0edc9ca87bc02f8ca5acb209d47913fa6a7d45133b3d4a16354a75421e32e"

    // Add the partecipants
    manager.addPartecipant("Alice", a_pub, "127.0.0.1", 25000)
    manager.addPartecipant("Bob", b_pub, "127.0.0.1", 25001)
    manager.addPartecipant("Arbiter", c_pub, "127.0.0.1", 25002)


    // Creating chunks and entries for transactions
    val t_chunks = manager.createPublicChunk(a_pub)

    val t1_alice_chunk_0 = manager.createAuthChunk(a_pub, 1)
    val t1_alice_chunk_1 = manager.createAuthChunk(b_pub)
    val t1_alice_chunks = manager.prepareEntry(t1_alice_chunk_0, t1_alice_chunk_1)

    val t1_bob_chunk_0 = manager.createAuthChunk(a_pub, 1)
    val t1_bob_chunk_1 = manager.createAuthChunk(b_pub)
    val t1_bob_chunks = manager.prepareEntry(t1_bob_chunk_0, t1_bob_chunk_1)

    val t1_c_bob_chunk_0 = manager.createAuthChunk(c_pub, 1)
    val t1_c_bob_chunk_1 = manager.createAuthChunk(b_pub)
    val t1_c_bob_chunks = manager.prepareEntry(t1_c_bob_chunk_0, t1_c_bob_chunk_1)

    val t1_c_alice_chunk_0 = manager.createAuthChunk(c_pub, 1)
    val t1_c_alice_chunk_1 = manager.createAuthChunk(a_pub)
    val t1_c_alice_chunks = manager.prepareEntry(t1_c_alice_chunk_0, t1_c_alice_chunk_1)

    val t1_c_split_alice_chunk_0 = manager.createAuthChunk(c_pub, 1)
    val t1_c_split_alice_chunk_1 = manager.createAuthChunk(a_pub)
    val t1_c_split_alice_chunks = manager.prepareEntry(t1_c_split_alice_chunk_0, t1_c_split_alice_chunk_1)

    val t1_c_split_bob_chunk_0 = manager.createAuthChunk(c_pub, 1)
    val t1_c_split_bob_chunk_1 = manager.createAuthChunk(b_pub)
    val t1_c_split_bob_chunks = manager.prepareEntry(t1_c_split_bob_chunk_0, t1_c_split_bob_chunk_1)

    // Linking amounts to entries
    val t_entry = manager.createEntry(1 btc, t_chunks)
    val t1_alice_entry = manager.createEntry(0.99 btc, t1_alice_chunks)
    val t1_bob_entry = manager.createEntry(0.99 btc, t1_bob_chunks)
    val t1_c_alice_entry = manager.createEntry(0.99 btc, t1_c_alice_chunks)
    val t1_c_bob_entry = manager.createEntry(0.99 btc, t1_c_bob_chunks)
    val t1_c_split_alice_entry = manager.createEntry(0.99 btc, t1_c_split_alice_chunks)
    val t1_c_split_bob_entry = manager.createEntry(0.99 btc, t1_c_split_bob_chunks)

    // Add the metadata of the transactions
    manager.addMeta("T", t_entry)
    manager.addMeta("T1_bob", t1_bob_entry)
    manager.addMeta("T1_alice", t1_alice_entry)
    manager.addMeta("T1_C_bob", t1_c_bob_entry)
    manager.addMeta("T1_C_alice", t1_c_alice_entry)
    manager.addMeta("T1_C_split_alice", t1_c_split_alice_entry)
    manager.addMeta("T1_C_split_bob", t1_c_split_bob_entry)

    // Get the initial state and return it
    manager getState
  }
}
