package Tests_Decentralized

import Managers._
import Managers.Helpers._
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
    manager addTransactions ("T" withRawTx "0200000000010102d46cc99398582e0c7a1f4817243d248e22596d8a2f541e533d5407692ea7030000000000ffffffff01c09ee605000000002200203a5fcea85c2dca4480bd79f80bff55356b4779c6b5ef870a6c8086c1285e932f02483045022100a2931bd002bfa506019758ce79bb3d9ddfac8e7befd44a64cb31b73919c99079022078cdff077483f5eed4573148f9f0b064892aabd8089f7b564785d19e58527875012103fd3c8b7437f9c8b447a3d04aca9ffa04c430c324a49495f13d116395029aa93a00000000",
                            "T1" withRawTx "02000000000101ee216806ef5b70a0d2a6453e5cad08180703795f8193cdb90c2585413e797ada0000000000ffffffff01805cd70500000000160014d5ee2547793a01cd846e1347ba907821493587df04000000475221028c96545ee165f631de2889ac3dd21bdf96efc7b9b92accc36c2107460c72ced721032ad0edc9ca87bc02f8ca5acb209d47913fa6a7d45133b3d4a16354a75421e32e52ae00000000")

    // Public keys
    val a_pub = "03fd3c8b7437f9c8b447a3d04aca9ffa04c430c324a49495f13d116395029aa93a"
    val b_pub = "028c96545ee165f631de2889ac3dd21bdf96efc7b9b92accc36c2107460c72ced7"
    val o_pub = "032ad0edc9ca87bc02f8ca5acb209d47913fa6a7d45133b3d4a16354a75421e32e"

    // Add the partecipants
    manager addPartecipants ("Alice" withInfo (a_pub, "127.0.0.1", 25000),
                            "Bob" withInfo (b_pub, "127.0.0.1", 25001),
                            "Oracle" withInfo (o_pub, "127.0.0.1", 25002))

    // Creating chunks and entries for transactions
    val t_chunks = manager createPublicChunk a_pub //implicit index=0

    val t1_chunks = manager prepareEntry (manager createAuthChunk (b_pub onIndex 1),
                                          manager createAuthChunk (o_pub onIndex 2))

    // Linking amounts to entries
    val t_entry = manager createEntry ((1 btc) forChunks t_chunks)
    val t1_entry = manager createEntry ((0.99 btc) forChunks t1_chunks)

    // Add the metadata of the transactions
    manager addMetas ("T" withEntry t_entry,
                     "T1" withEntry t1_entry)

    // Get the initial state and return it
    manager getState
  }
}
