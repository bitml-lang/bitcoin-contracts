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

    //d209c9e83a79ae7eeb279f094125ea178ee0351b867ceae498bc26786588840d
    //0d8488657826bc98e4ea7c861b35e08e17ea2541099f27eb7eae793ae8c909d2
    //e749e7a32ed80c02d1a0571dba62dcbe07db5988d399581fd77dc9f0fe35e1ed
    //ede135fef0c97dd71f5899d38859db07bedc62ba1d57a0d1020cd82ea3e749e7
    // Add the serialized transactions that we need
    manager.addTransaction("T", "02000000010d8488657826bc98e4ea7c861b35e08e17ea2541099f27eb7eae793ae8c909d2000000006a4730440220243f4eeb0fe3c495495ce84a9d1e80710727842a5df2f6f309756d9a203e691b02201fd5d92cfb6e35e569c21f8a0492f5564a0eacfa4f24f1857fa613dfdb1b309d012103fd3c8b7437f9c8b447a3d04aca9ffa04c430c324a49495f13d116395029aa93affffffff01c09ee6050000000017a914065a6d907d4ecdc69b2517478b131612fb810b538700000000")
    manager.addTransaction("T1", "0200000001ede135fef0c97dd71f5899d38859db07bedc62ba1d57a0d1020cd82ea3e749e700000000e4483045022100f61ff0c8e60efccbdaaf467bdc8574ec12848c53b34ac99316f2beb890d5ab4002207be3e4b17c4c6d449c9fd26d1bcecd2832d22526013900bbaef539fb91e177410147304402205137c4ea66d6dcb5d670790ee15aa5896fc86ee9b75bacb3ed062d83a2ed1d2b02200610cee1f0d3a9603093dbffd920161a54e4e3aed65ee32a87059b5cc47a4842014c516b6b006c766c766b7c6b5221028c96545ee165f631de2889ac3dd21bdf96efc7b9b92accc36c2107460c72ced721032ad0edc9ca87bc02f8ca5acb209d47913fa6a7d45133b3d4a16354a75421e32e52aeffffffff01805cd705000000001976a914d5ee2547793a01cd846e1347ba907821493587df88ac00000000")

    // Public keys
    val a_pub = "03fd3c8b7437f9c8b447a3d04aca9ffa04c430c324a49495f13d116395029aa93a"
    val b_pub = "028c96545ee165f631de2889ac3dd21bdf96efc7b9b92accc36c2107460c72ced7"
    val o_pub = "032ad0edc9ca87bc02f8ca5acb209d47913fa6a7d45133b3d4a16354a75421e32e"

    // Add the partecipants
    manager.addPartecipant("Alice", a_pub, "127.0.0.1", 25000)
    manager.addPartecipant("Bob", b_pub, "127.0.0.1", 25001)
    manager.addPartecipant("Oracle", o_pub, "127.0.0.1", 25002)


    // Creating chunks and entries for transactions
    val t_chunks = manager.createChunk(a_pub, 0, ChunkType.SIG_P2PKH)

    val t1_chunk_0 = manager.createChunk(b_pub, 0, ChunkType.SIG_P2SH)
    val t1_chunk_1 = manager.createChunk(o_pub, 1, ChunkType.SIG_P2SH, chunkPrivacy = ChunkPrivacy.AUTH)
    val t1_chunks = manager.prepareEntry(t1_chunk_0, t1_chunk_1)


    // Linking amounts to entries
    val t_entry = manager.createEntry(1 btc, t_chunks)
    val t1_entry = manager.createEntry(0.99 btc, t1_chunks)

    // Add the metadata of the transactions
    manager.addMeta("T", t_entry)
    manager.addMeta("T1", t1_entry)

    // Get the initial state and return it
    manager getState
  }
}
