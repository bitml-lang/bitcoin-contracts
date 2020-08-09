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
import Managers._
import Managers.Helpers._

import scala.concurrent.duration._
import scala.concurrent.Await

object Setup {
  def setup(): State = {

    // Create an istance of DbManager to store the data
    val manager = DbManager()

    // Add the serialized transactions that we need
    manager.addTransactions("alice_T_commit" withRawTx "0200000000010198a3bf3f848b5463e1d1dde954f7806fd145f8a04d605e4f17351ef9a63e6cf90000000000ffffffff016043993b00000000220020acdd9f98aa65e299f14ebefd177cc6cd66d84258b0818599cb456752c4079f4602483045022100e72515d15e2e2f130b83b74b49033996ccee7487d6245decfe78c1e2ad051f0602203f8a91e90f2422b6bf2b5f9fa3cbaab861e3f1724e74236ee78ea53cc6b57e80012103fd3c8b7437f9c8b447a3d04aca9ffa04c430c324a49495f13d116395029aa93a00000000",
                            "alice_T_reveal" withRawTx "0200000000010178e2a50f5b89d92d1b0cbd79169337c3ec1d392f7e9e7620715ca6773562e8b20000000000ffffffff01c0bc973b000000001600140b09d7a4d5ec473247d259d6e066b6596807619d03000082766b7c6ba82073475cb40a568e8da8a045ced110137e159f890ac4da883b6b17dc651b3a804987636c766b2103fd3c8b7437f9c8b447a3d04aca9ffa04c430c324a49495f13d116395029aa93aac6700686351670480bb1d5bb1756c766b21028c96545ee165f631de2889ac3dd21bdf96efc7b9b92accc36c2107460c72ced7ac6800000000",
                            "bob_T_timeout" withRawTx  "0200000000010178e2a50f5b89d92d1b0cbd79169337c3ec1d392f7e9e7620715ca6773562e8b20000000000ffffffff01c0bc973b000000001600140b09d7a4d5ec473247d259d6e066b6596807619d03000082766b7c6ba82073475cb40a568e8da8a045ced110137e159f890ac4da883b6b17dc651b3a804987636c766b2103fd3c8b7437f9c8b447a3d04aca9ffa04c430c324a49495f13d116395029aa93aac6700686351670480bb1d5bb1756c766b21028c96545ee165f631de2889ac3dd21bdf96efc7b9b92accc36c2107460c72ced7ac6800000000")

    // Public keys
    val a_pub = "03fd3c8b7437f9c8b447a3d04aca9ffa04c430c324a49495f13d116395029aa93a"
    val b_pub = "028c96545ee165f631de2889ac3dd21bdf96efc7b9b92accc36c2107460c72ced7"

    // Add the partecipants
    manager.addPartecipants("Alice" withInfo (a_pub, "127.0.0.1", 25000),
                            "Bob" withInfo (b_pub, "127.0.0.1", 25001))

    // Creating chunks and entries for transactions
    val alice_t_commit_chunk = manager createPublicChunk a_pub

    val t_reveal_chunks = manager prepareEntry(manager createAuthChunk a_pub,
                                                manager createSecretChunk(a_pub withSecret "3432"))

    val bob_t_timeout_chunks = manager prepareEntry(manager createAuthChunk b_pub,
                                                    manager createSecretChunk(b_pub withSecret "00"))
    // Linking amounts to entries
    val t_commit_entry = manager createEntry((10 btc) forChunks alice_t_commit_chunk)
    val t_reveal_entry = manager createEntry((9.999 btc) forChunks t_reveal_chunks)
    val t_timeout_entry = manager createEntry((9.999 btc) forChunks bob_t_timeout_chunks)

    // Add the metadata of the transactions
    manager.addMetas("alice_T_commit" withEntry t_commit_entry,
                      "alice_T_reveal" withEntry t_reveal_entry,
                      "bob_T_timeout" withEntry t_timeout_entry)

    // Get the initial state and return it
    manager getState
  }
}
