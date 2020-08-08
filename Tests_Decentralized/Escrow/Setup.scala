package Tests_Decentralized

import Managers.DbManager
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
    manager addTransactions ("T" withRawTx "02000000000101906dc25636e74dcdd32abd77895a92484d5775e4308b6dc89075c8b936cd11970000000000ffffffff01c09ee6050000000022002061380d8e7a652ddb421264cea9870ff21c444f0ba67cb8298c09a3cb3d04d23102483045022100b39f3a2f863c694e8219e0de65cc73979a853b4b6dc466ca09000c48fcc905de02207aad6b9d37229cf4c4e8390d7f855f3e9aeeea8434b41dcf2a7a48b181d11de2012103fd3c8b7437f9c8b447a3d04aca9ffa04c430c324a49495f13d116395029aa93a00000000",
                             "T1_bob" withRawTx "0200000000010137f593980bab7a27b6931eb08d3fedc01732a9e0ab76838a92fdd4185c28d11f0000000000ffffffff01805cd70500000000160014d5ee2547793a01cd846e1347ba907821493587df0400000069522103fd3c8b7437f9c8b447a3d04aca9ffa04c430c324a49495f13d116395029aa93a21028c96545ee165f631de2889ac3dd21bdf96efc7b9b92accc36c2107460c72ced721032ad0edc9ca87bc02f8ca5acb209d47913fa6a7d45133b3d4a16354a75421e32e53ae00000000",
                             "T1_alice" withRawTx "0200000000010137f593980bab7a27b6931eb08d3fedc01732a9e0ab76838a92fdd4185c28d11f0000000000ffffffff01805cd705000000001600140b09d7a4d5ec473247d259d6e066b6596807619d0400000069522103fd3c8b7437f9c8b447a3d04aca9ffa04c430c324a49495f13d116395029aa93a21028c96545ee165f631de2889ac3dd21bdf96efc7b9b92accc36c2107460c72ced721032ad0edc9ca87bc02f8ca5acb209d47913fa6a7d45133b3d4a16354a75421e32e53ae00000000",
                             "T1_C_bob" withRawTx "0200000000010137f593980bab7a27b6931eb08d3fedc01732a9e0ab76838a92fdd4185c28d11f0000000000ffffffff01805cd70500000000160014d5ee2547793a01cd846e1347ba907821493587df0400000069522103fd3c8b7437f9c8b447a3d04aca9ffa04c430c324a49495f13d116395029aa93a21028c96545ee165f631de2889ac3dd21bdf96efc7b9b92accc36c2107460c72ced721032ad0edc9ca87bc02f8ca5acb209d47913fa6a7d45133b3d4a16354a75421e32e53ae00000000",
                             "T1_C_alice" withRawTx "0200000000010137f593980bab7a27b6931eb08d3fedc01732a9e0ab76838a92fdd4185c28d11f0000000000ffffffff01805cd705000000001600140b09d7a4d5ec473247d259d6e066b6596807619d0400000069522103fd3c8b7437f9c8b447a3d04aca9ffa04c430c324a49495f13d116395029aa93a21028c96545ee165f631de2889ac3dd21bdf96efc7b9b92accc36c2107460c72ced721032ad0edc9ca87bc02f8ca5acb209d47913fa6a7d45133b3d4a16354a75421e32e53ae00000000",
                             "T1_C_split_alice" withRawTx "0200000000010137f593980bab7a27b6931eb08d3fedc01732a9e0ab76838a92fdd4185c28d11f0000000000ffffffff0240aeeb02000000001600140b09d7a4d5ec473247d259d6e066b6596807619d40aeeb0200000000160014d5ee2547793a01cd846e1347ba907821493587df0400000069522103fd3c8b7437f9c8b447a3d04aca9ffa04c430c324a49495f13d116395029aa93a21028c96545ee165f631de2889ac3dd21bdf96efc7b9b92accc36c2107460c72ced721032ad0edc9ca87bc02f8ca5acb209d47913fa6a7d45133b3d4a16354a75421e32e53ae00000000",
                             "T1_C_split_bob" withRawTx "0200000000010137f593980bab7a27b6931eb08d3fedc01732a9e0ab76838a92fdd4185c28d11f0000000000ffffffff0240aeeb02000000001600140b09d7a4d5ec473247d259d6e066b6596807619d40aeeb0200000000160014d5ee2547793a01cd846e1347ba907821493587df0400000069522103fd3c8b7437f9c8b447a3d04aca9ffa04c430c324a49495f13d116395029aa93a21028c96545ee165f631de2889ac3dd21bdf96efc7b9b92accc36c2107460c72ced721032ad0edc9ca87bc02f8ca5acb209d47913fa6a7d45133b3d4a16354a75421e32e53ae00000000")

    // Public keys
    val a_pub = "03fd3c8b7437f9c8b447a3d04aca9ffa04c430c324a49495f13d116395029aa93a"
    val b_pub = "028c96545ee165f631de2889ac3dd21bdf96efc7b9b92accc36c2107460c72ced7"
    val c_pub = "032ad0edc9ca87bc02f8ca5acb209d47913fa6a7d45133b3d4a16354a75421e32e"

    // Add the partecipants
    manager addPartecipants ("Alice" withInfo (a_pub, "127.0.0.1", 25000),
                             "Bob" withInfo (b_pub, "127.0.0.1", 25001),
                             "Arbiter"withInfo (c_pub, "127.0.0.1", 25002))

    // Creating chunks and entries for transactions
    //this public chunk is gonna be on index 0 and is gonna contain Alice's signature
    val t_chunks = manager createPublicChunk a_pub

    //this chunks have to be authorized by Alice and Bob and they are gonna contain their signatures for "T"
    val t1_alice_chunks = manager prepareEntry (manager createAuthChunk(a_pub onIndex 1),
                                                manager createAuthChunk(b_pub onIndex 2))

    val t1_bob_chunks = manager prepareEntry (manager createAuthChunk(a_pub onIndex 1),
                                              manager createAuthChunk(b_pub onIndex 2))

    val t1_c_bob_chunks = manager prepareEntry (manager createAuthChunk(c_pub onIndex 1),
                                                manager createAuthChunk(b_pub onIndex 2))

    val t1_c_alice_chunks = manager prepareEntry (manager createAuthChunk(c_pub onIndex 1),
                                                  manager createAuthChunk(a_pub onIndex 2))

    val t1_c_split_alice_chunks = manager prepareEntry (manager createAuthChunk(a_pub onIndex 1),
                                                        manager createAuthChunk(c_pub onIndex 2))

    val t1_c_split_bob_chunks = manager prepareEntry (manager createAuthChunk(b_pub onIndex 1),
                                                      manager createAuthChunk(c_pub onIndex 2))

    // Linking amounts to entries, every bitcoin amount has to be linked to the signatures in order to make the right signatures
    // This is necessary since a recent BIP (Bitcoin Improvement Proposal) makes difficult in segwit transactions to just take the bitcoin amount from previous tx
    val t_entry = manager createEntry ((1 btc) forChunks t_chunks)
    val t1_alice_entry = manager createEntry ((0.99 btc) forChunks t1_alice_chunks)
    val t1_bob_entry = manager createEntry ((0.99 btc) forChunks t1_bob_chunks)
    val t1_c_alice_entry = manager createEntry ((0.99 btc) forChunks t1_c_alice_chunks)
    val t1_c_bob_entry = manager createEntry ((0.99 btc) forChunks t1_c_bob_chunks)
    val t1_c_split_alice_entry = manager createEntry ((0.99 btc) forChunks t1_c_split_alice_chunks)
    val t1_c_split_bob_entry = manager createEntry ((0.99 btc) forChunks  t1_c_split_bob_chunks)

    // Add the metadata of the transactions, for every raw transaction previously added we save the metadata we just created
    manager addMetas ("T" withEntry t_entry,
                      "T1_bob" withEntry t1_bob_entry,
                      "T1_alice" withEntry t1_alice_entry,
                      "T1_C_bob" withEntry t1_c_bob_entry,
                      "T1_C_alice" withEntry t1_c_alice_entry,
                      "T1_C_split_alice" withEntry t1_c_split_alice_entry,
                      "T1_C_split_bob" withEntry t1_c_split_bob_entry)

    // Get the initial state and return it
    manager getState
  }
}
