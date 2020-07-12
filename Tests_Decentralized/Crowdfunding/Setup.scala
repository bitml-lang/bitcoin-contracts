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
    manager.addTransaction("V", "02000000000104a5fa6e1e3d7eba684c088c9c1e1fee6e26fa3ffcbac23bba7140fcb3aa8a58b90000000000ffffffff8ee7294baf70a393147f9aad4c2cf10a304c8e58bd825b4a0ffd56c78203d6890000000000ffffffffef95906b653b6342e9162f6910450bc801a2ffc845aa0c7acdb83d6553a295070000000000ffffffffd03369325394904861020fe5376bc48b9371a677af99c1f53c067a5af8c9f9250000000000ffffffff01c041c817000000001600140a907ebf3fcbc8e61578291dc5b28793c23b805302002103fd3c8b7437f9c8b447a3d04aca9ffa04c430c324a49495f13d116395029aa93a020021028c96545ee165f631de2889ac3dd21bdf96efc7b9b92accc36c2107460c72ced702002102da5927ba9e32d6dae07358cc2a741c4c96c6b10e33a7a5063b9255de9b1132b30200210236678af2b144efb6e59388d86d7f1917c3991172914430c4f1784dc3543baf8d00000000")

    // Public keys
    val a1_pub = "03fd3c8b7437f9c8b447a3d04aca9ffa04c430c324a49495f13d116395029aa93a"
    val a2_pub = "028c96545ee165f631de2889ac3dd21bdf96efc7b9b92accc36c2107460c72ced7"
    val a3_pub = "02da5927ba9e32d6dae07358cc2a741c4c96c6b10e33a7a5063b9255de9b1132b3"
    val a4_pub = "0236678af2b144efb6e59388d86d7f1917c3991172914430c4f1784dc3543baf8d"
    val curator_pub = "032ad0edc9ca87bc02f8ca5acb209d47913fa6a7d45133b3d4a16354a75421e32e"

    // Add the partecipants
    manager.addPartecipant("A1", Seq(a1_pub), "127.0.0.1", 25001)
    manager.addPartecipant("A2", Seq(a2_pub), "127.0.0.1", 25002)
    manager.addPartecipant("A3", Seq(a3_pub), "127.0.0.1", 25003)
    manager.addPartecipant("A4", Seq(a4_pub), "127.0.0.1", 25004)
    manager.addPartecipant("Curator", Seq(curator_pub), "127.0.0.1", 25005)


    // Creating chunks and entries for transactions
    val v_chunks_0 = Seq(manager.createChunk(a1_pub, 0, chunkPrivacy = ChunkPrivacy.AUTH))
    val v_chunks_1 = Seq(manager.createChunk(a2_pub, 0, chunkPrivacy = ChunkPrivacy.AUTH))
    val v_chunks_2 = Seq(manager.createChunk(a3_pub, 0, chunkPrivacy = ChunkPrivacy.AUTH))
    val v_chunks_3 = Seq(manager.createChunk(a4_pub, 0, chunkPrivacy = ChunkPrivacy.AUTH))

    // Linking amounts to entries
    val v_entry_0 = manager.createEntry(1 btc, v_chunks_0)
    val v_entry_1 = manager.createEntry(1 btc, v_chunks_1)
    val v_entry_2 = manager.createEntry(1 btc, v_chunks_2)
    val v_entry_3 = manager.createEntry(1 btc, v_chunks_3)


    // Add the metadata of the transactions
    manager.addMeta("V", Seq(v_entry_0, v_entry_1, v_entry_2, v_entry_3))

    // Get the initial state and return it
    manager getState
  }
}
