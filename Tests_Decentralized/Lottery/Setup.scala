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

    manager addTransactions ("fundsA" withRawTx "02000000000101e99eba6aeb3ce2f8efbb4714a21f638fad7da6642b11c8b08b0e69b25f91a4a10000000000ffffffff01a067f705000000001976a9140b09d7a4d5ec473247d259d6e066b6596807619d88ac02483045022100c12f6311edcff8b178e2437e0c8a9c08fdf0072eef7b3c38507b229c1a762a180220610ede0ebf1396e419844e1730cc1002d14478f7c7f29e15786f55d90af624b1012103fd3c8b7437f9c8b447a3d04aca9ffa04c430c324a49495f13d116395029aa93a00000000",
                             "fundsB" withRawTx "020000000001015db1ab507a7c3e3889d62f69b09f77e9ccecf94e3b1bb0d4d31e2d39ae283ab10100000000ffffffff01a067f705000000001976a914d5ee2547793a01cd846e1347ba907821493587df88ac02483045022100967bee2f853835fbc2e9b01bd93c203ecbb7f0d431e93c5e1e62100fd37079bb02202f284246dc254f8cfbceb5120fecebf98cca3f317a961df9a723669f72b653980121028c96545ee165f631de2889ac3dd21bdf96efc7b9b92accc36c2107460c72ced700000000",
                             "Tinit" withRawTx "0200000002a6e2c753fe413254ce166a9d8991b6d535d37d672c8d0329a120a32c49248782000000006a4730440220399f30c422e3a2106c6c01e86307a8b5aaf41972c72f9909e85d29374e476590022061f347f7a0f711c087952b4a5a75b6b9afa2c6d69c546378a76aa68a6c0528a9012103fd3c8b7437f9c8b447a3d04aca9ffa04c430c324a49495f13d116395029aa93affffffff0279bf96709539cf59b40227b827fdd1f8c4f594e41ce4f29f39079e3bf519fe000000006a47304402204e762275c5ce6e79e1b0ff0c8cc49cc832056ab4006900eadb07668c9c27f0f702202a422419b01bcd4fa280ce736fa8acfae7356791db8a188f13439388691680c90121028c96545ee165f631de2889ac3dd21bdf96efc7b9b92accc36c2107460c72ced7ffffffff0100c2eb0b0000000017a914372c54532bc734ca6534cc22a8f5b98a21fb24ed8700000000",
                             "T1" withRawTx "02000000010ba86efe0e541f3928036bf4d267996044d8fd09d98c65b4dc11457040f4837600000000e4473044022074199052dcad1d0b01f75252a20ea969c3d65cdb3bc1feeb2cb460b88237157e022057f7633e12aa5fea2512bd8e4620734715b651c4ca61e8b5e1a5a1b39c8f15c701483045022100c02e5dd3997b5b7a93d488027682e3fe049b569527672e9f5be17380b51b9dee02204da98a86d11d8d56196905f4051677dfe9f0d28d599ba0002ee5512f0210e152014c516b6b006c766c766b7c6b5221028c96545ee165f631de2889ac3dd21bdf96efc7b9b92accc36c2107460c72ced72103fd3c8b7437f9c8b447a3d04aca9ffa04c430c324a49495f13d116395029aa93a52aeffffffff01c07fdc0b0000000017a914063e4c1ecae3206bdc95de7e2db9d11b7b2b57c78700000000",
                             "TwinA" withRawTx "0200000001b22715682c05044977142f4a429063a9c762940cc89acaaab32d33588d8d797600000000fd99034c8030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030304c803030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303031483045022100cfcc8e7da32743af35bbaaafac1b99fb9ba160a2b0fc3160d6ea2b335e82f13b022034469def4dd4a5c44ac94c1d84931983c432116c240a45dbc9cedc41abfb9a4d01483045022100ed83ca5cf497bda84d4ed2ec055ff11a58bc2c7842dfa99a0476fc79b850e2c80220690204e86b8fb7b95193cbf8d812315976170dad68525fb052b1c4983eb84cd2014d00026b6b6b766b827c756c6c766b7c6b827c7587636c766ba914b472a266d0bd89c13706a4132ccfb16f7c3b9fcb87670068636c766b827c75028000a2670068636c6c766b7c6ba914c51b66bced5e4491001bd702669770dccf44098287670068636c6c766b7c6b827c75028000a267006863006c6c6c766b7c6b7c6c6c6c766b7c6b7c6b7c6b5221028c96545ee165f631de2889ac3dd21bdf96efc7b9b92accc36c2107460c72ced72103fd3c8b7437f9c8b447a3d04aca9ffa04c430c324a49495f13d116395029aa93a52ae6700686351676c766b827c756c6c766b7c6b827c758791636c766ba914b472a266d0bd89c13706a4132ccfb16f7c3b9fcb87670068636c766b827c75028000a2670068636c6c766b7c6ba914c51b66bced5e4491001bd702669770dccf44098287670068636c6c766b7c6b827c75028000a267006863006c6c6c766b7c6b7c6c6c6c766b7c6b7c6b7c6b5221028c96545ee165f631de2889ac3dd21bdf96efc7b9b92accc36c2107460c72ced72103fd3c8b7437f9c8b447a3d04aca9ffa04c430c324a49495f13d116395029aa93a52ae67006868635167006c6c6c766b7c6b7c6c6c6c766b7c6b7c6b7c6b5221028c96545ee165f631de2889ac3dd21bdf96efc7b9b92accc36c2107460c72ced72103fd3c8b7437f9c8b447a3d04aca9ffa04c430c324a49495f13d116395029aa93a52ae68ffffffff01805cd705000000001976a9140b09d7a4d5ec473247d259d6e066b6596807619d88ac00000000")

    val a_pub = "03fd3c8b7437f9c8b447a3d04aca9ffa04c430c324a49495f13d116395029aa93a"
    val b_pub = "028c96545ee165f631de2889ac3dd21bdf96efc7b9b92accc36c2107460c72ced7"
    val a2_pub = PublicKey(ByteVector.fromValidHex(a_pub))
    val b2_pub = PublicKey(ByteVector.fromValidHex(b_pub))

    manager addPartecipants ("Alice" withInfo (a_pub, "127.0.0.1", 25000),
                             "Bob" withInfo (b_pub, "127.0.0.1", 25001))

    val fundsa_chunks = manager createPublicChunk(a_pub, chunkType = ChunkType.SIG_P2WPKH)
    val fundsb_chunks = manager createPublicChunk(b_pub, chunkType = ChunkType.SIG_P2WPKH)

    val tinit0_chunks = manager.createAuthChunk(a_pub, chunkType = ChunkType.SIG_P2PKH, chunkPrivacy = ChunkPrivacy.AUTH)
    val tinit1_chunks = manager.createAuthChunk(b_pub, chunkType = ChunkType.SIG_P2PKH, chunkPrivacy = ChunkPrivacy.AUTH)

    val t1_chunks = manager prepareEntry (manager createAuthChunk(b_pub, index = 0, chunkType = ChunkType.SIG_P2SH),
                                          manager createAuthChunk(a_pub, index = 1, chunkType = ChunkType.SIG_P2SH))
    val twina_chunks = Seq(
      ChunkEntry(chunkType = ChunkType.SECRET_IN, chunkPrivacy= ChunkPrivacy.PUBLIC, chunkIndex = 0, owner = Option(a2_pub), data = ByteVector.fromValidHex("3030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030")),
      ChunkEntry(chunkType = ChunkType.SECRET_IN, chunkPrivacy= ChunkPrivacy.PUBLIC, chunkIndex = 1, owner = Option(b2_pub), data = ByteVector.fromValidHex("3030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303031")),
      manager.createPublicChunk(a_pub, index = 2, chunkType = ChunkType.SIG_P2WSH)(0),
      manager.createPublicChunk(b_pub, index = 3, chunkType = ChunkType.SIG_P2WSH)(0))

    val fundsa_entry = manager createEntry ((1.002 btc), fundsa_chunks)
    val fundsb_entry = manager createEntry ((1.002 btc), fundsb_chunks)

    //val tinit0_entry = manager createEntry ((0.5 btc) forChunks tinit0_chunks)
    val tinit0_entry = manager createEntry(1.001 btc, tinit0_chunks)
    //val tinit1_entry = manager createEntry ((0.5 btc) forChunks tinit1_chunks)
    val tinit1_entry = manager createEntry(1.001 btc, tinit1_chunks)

    val t1_entry = manager createEntry ((2 btc) forChunks t1_chunks)

    val twina_entry = manager createEntry((1.99 btc), twina_chunks)

    manager addMetas ("fundsA" withEntry fundsa_entry,
                      "fundsB" withEntry fundsb_entry)
    manager.addMeta("Tinit", Seq(tinit0_entry, tinit1_entry))
    manager addMetas ("T1" withEntry t1_entry,
                      "TwinA" withEntry twina_entry)

      // Add the serialized transactions that we need
    /*manager addTransactions ("fundsA" withRawTx "020000000001019fba114eda28b2995ac5ca69ccf7749eebafacfec30ccf711bd7047a1a5475d60000000000ffffffff01a067f705000000001976a9140b09d7a4d5ec473247d259d6e066b6596807619d88ac02483045022100b0b49d48c3f675a5aa2c83dd52cc9c01651fd709352429c8c00ad0f7797ed0960220555ed0b7526e727b3d34c05734debccbaf1c957b5cc7961bee5d2f7cd0ab87d3012103fd3c8b7437f9c8b447a3d04aca9ffa04c430c324a49495f13d116395029aa93a00000000",
                            "fundsB" withRawTx "020000000001017f1d721ae08f153963d34fede18e40a683283b36471cd0504e2c626b3549f1b70000000000ffffffff01a067f705000000001976a914d5ee2547793a01cd846e1347ba907821493587df88ac0247304402204d30d1ce29f28dfa465f64b75f7e437d53e4e984ba2c217df4c2036b4b0e1c53022030093dea8e8d6f43efd2b956c75c9f2d0f5bba49972ad2c30f0f6ce4988156b60121028c96545ee165f631de2889ac3dd21bdf96efc7b9b92accc36c2107460c72ced700000000",
                            "Tinit" withRawTx "0200000002f2ac10a469d36cd2ba8c7d554a1ab0f603a0d7c57a6dcc1c8d4cb550eb2ab7d7000000006a473044022016136fe9cca91a469af0ea57496e60fe385531f60fefe873875c00484567987502200e37f6afcf5a95e82fafcc876c5fdc4f982516cc44a0d46861c5b4adc730b500012103fd3c8b7437f9c8b447a3d04aca9ffa04c430c324a49495f13d116395029aa93affffffff95bdc1f5da538f456687396f5df39416e61d8f1071286a6d5c9676918b91b398000000006a47304402203491cbbde9b3f114b63617dd17b0d2204f6dbb6bb7884acd839851de12b18bbd02204f5c6ec8d4f5e632cc215d6be5be8380bf33fdd00ccd999ed5340c466ecd118f0121028c96545ee165f631de2889ac3dd21bdf96efc7b9b92accc36c2107460c72ced7ffffffff0100e1f5050000000017a914372c54532bc734ca6534cc22a8f5b98a21fb24ed8700000000",
                            "T1" withRawTx "0200000001584924edd0a822495d48fd1322f8c2e95d993125b521d430e4cd637dcde4098300000000e347304402207dcab9a576d6aebde3f5dbd638e24e11e8af90c160d12debbe74347263a32aa602205b43d08003f9dcf84c65e2431eb93d4e8b2496a037edc0068180c030f6144dae0147304402202334c2089a92d8acac326e46f1d619d7e1219adb7680b3b505df9392fc78a6be02201b0c7a6db382ce91913d7de2543caed539c8e98a95f363120759b5978ab4c94c014c516b6b006c766c766b7c6b5221028c96545ee165f631de2889ac3dd21bdf96efc7b9b92accc36c2107460c72ced72103fd3c8b7437f9c8b447a3d04aca9ffa04c430c324a49495f13d116395029aa93a52aeffffffff01c09ee6050000000017a914063e4c1ecae3206bdc95de7e2db9d11b7b2b57c78700000000",
                            "TwinA" withRawTx "0200000001b22715682c05044977142f4a429063a9c762940cc89acaaab32d33588d8d797600000000fd99034c8030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030304c803030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303031483045022100cfcc8e7da32743af35bbaaafac1b99fb9ba160a2b0fc3160d6ea2b335e82f13b022034469def4dd4a5c44ac94c1d84931983c432116c240a45dbc9cedc41abfb9a4d01483045022100ed83ca5cf497bda84d4ed2ec055ff11a58bc2c7842dfa99a0476fc79b850e2c80220690204e86b8fb7b95193cbf8d812315976170dad68525fb052b1c4983eb84cd2014d00026b6b6b766b827c756c6c766b7c6b827c7587636c766ba914b472a266d0bd89c13706a4132ccfb16f7c3b9fcb87670068636c766b827c75028000a2670068636c6c766b7c6ba914c51b66bced5e4491001bd702669770dccf44098287670068636c6c766b7c6b827c75028000a267006863006c6c6c766b7c6b7c6c6c6c766b7c6b7c6b7c6b5221028c96545ee165f631de2889ac3dd21bdf96efc7b9b92accc36c2107460c72ced72103fd3c8b7437f9c8b447a3d04aca9ffa04c430c324a49495f13d116395029aa93a52ae6700686351676c766b827c756c6c766b7c6b827c758791636c766ba914b472a266d0bd89c13706a4132ccfb16f7c3b9fcb87670068636c766b827c75028000a2670068636c6c766b7c6ba914c51b66bced5e4491001bd702669770dccf44098287670068636c6c766b7c6b827c75028000a267006863006c6c6c766b7c6b7c6c6c6c766b7c6b7c6b7c6b5221028c96545ee165f631de2889ac3dd21bdf96efc7b9b92accc36c2107460c72ced72103fd3c8b7437f9c8b447a3d04aca9ffa04c430c324a49495f13d116395029aa93a52ae67006868635167006c6c6c766b7c6b7c6c6c6c766b7c6b7c6b7c6b5221028c96545ee165f631de2889ac3dd21bdf96efc7b9b92accc36c2107460c72ced72103fd3c8b7437f9c8b447a3d04aca9ffa04c430c324a49495f13d116395029aa93a52ae68ffffffff01805cd705000000001976a9140b09d7a4d5ec473247d259d6e066b6596807619d88ac00000000",
                            "TwinB" withRawTx "0200000001b22715682c05044977142f4a429063a9c762940cc89acaaab32d33588d8d797600000000fd99034c8030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030304c803030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303031483045022100cc5a33827488e3c0c43d667dd90c031be059e5f1c4dd3119a9b2ba97aba69b99022016b0238fd3619c8015adf890bc882fe2edbc6fd118fbe79c21c824c6d06bc12801483045022100ed5c6de148a23b5ca1f6bd3f4723900e6193b3fbd5a85183853c68e1d6fec86e0220215991b62eefb1988109f1dda9f4a49f786a1da4e430f1893f7c9c7a459d9cfa014d00026b6b6b766b827c756c6c766b7c6b827c7587636c766ba914b472a266d0bd89c13706a4132ccfb16f7c3b9fcb87670068636c766b827c75028000a2670068636c6c766b7c6ba914c51b66bced5e4491001bd702669770dccf44098287670068636c6c766b7c6b827c75028000a267006863006c6c6c766b7c6b7c6c6c6c766b7c6b7c6b7c6b5221028c96545ee165f631de2889ac3dd21bdf96efc7b9b92accc36c2107460c72ced72103fd3c8b7437f9c8b447a3d04aca9ffa04c430c324a49495f13d116395029aa93a52ae6700686351676c766b827c756c6c766b7c6b827c758791636c766ba914b472a266d0bd89c13706a4132ccfb16f7c3b9fcb87670068636c766b827c75028000a2670068636c6c766b7c6ba914c51b66bced5e4491001bd702669770dccf44098287670068636c6c766b7c6b827c75028000a267006863006c6c6c766b7c6b7c6c6c6c766b7c6b7c6b7c6b5221028c96545ee165f631de2889ac3dd21bdf96efc7b9b92accc36c2107460c72ced72103fd3c8b7437f9c8b447a3d04aca9ffa04c430c324a49495f13d116395029aa93a52ae67006868635167006c6c6c766b7c6b7c6c6c6c766b7c6b7c6b7c6b5221028c96545ee165f631de2889ac3dd21bdf96efc7b9b92accc36c2107460c72ced72103fd3c8b7437f9c8b447a3d04aca9ffa04c430c324a49495f13d116395029aa93a52ae68ffffffff01805cd705000000001976a914d5ee2547793a01cd846e1347ba907821493587df88ac00000000",
                            "Tsplit" withRawTx "0200000001b22715682c05044977142f4a429063a9c762940cc89acaaab32d33588d8d797600000000fd930247304402204703c84558d58ff243d328a301945e637b81d4ec6d94e961dbe60388148cde4e02201bc04ea3cfda8cc4298b23967e15cabe88dd59c95cefddb78d091ed264d266a001473044022058929d935acea26e76c3e4f9a30d28ccc1da0c3a1090c199f7ef376c5dfec70a02207c04eda04184cccc8a6c948253098f310d09d5f1133b7176626049a6c68f162c014d00026b6b6b766b827c756c6c766b7c6b827c7587636c766ba914b472a266d0bd89c13706a4132ccfb16f7c3b9fcb87670068636c766b827c75028000a2670068636c6c766b7c6ba914c51b66bced5e4491001bd702669770dccf44098287670068636c6c766b7c6b827c75028000a267006863006c6c6c766b7c6b7c6c6c6c766b7c6b7c6b7c6b5221028c96545ee165f631de2889ac3dd21bdf96efc7b9b92accc36c2107460c72ced72103fd3c8b7437f9c8b447a3d04aca9ffa04c430c324a49495f13d116395029aa93a52ae6700686351676c766b827c756c6c766b7c6b827c758791636c766ba914b472a266d0bd89c13706a4132ccfb16f7c3b9fcb87670068636c766b827c75028000a2670068636c6c766b7c6ba914c51b66bced5e4491001bd702669770dccf44098287670068636c6c766b7c6b827c75028000a267006863006c6c6c766b7c6b7c6c6c6c766b7c6b7c6b7c6b5221028c96545ee165f631de2889ac3dd21bdf96efc7b9b92accc36c2107460c72ced72103fd3c8b7437f9c8b447a3d04aca9ffa04c430c324a49495f13d116395029aa93a52ae67006868635167006c6c6c766b7c6b7c6c6c6c766b7c6b7c6b7c6b5221028c96545ee165f631de2889ac3dd21bdf96efc7b9b92accc36c2107460c72ced72103fd3c8b7437f9c8b447a3d04aca9ffa04c430c324a49495f13d116395029aa93a52ae68feffffff02006cdc02000000001976a9140b09d7a4d5ec473247d259d6e066b6596807619d88ac006cdc02000000001976a914d5ee2547793a01cd846e1347ba907821493587df88ac60e31600")

    // Public keys
    val a_pub = "03fd3c8b7437f9c8b447a3d04aca9ffa04c430c324a49495f13d116395029aa93a"
    val b_pub = "028c96545ee165f631de2889ac3dd21bdf96efc7b9b92accc36c2107460c72ced7"
    val a2_pub = PublicKey(ByteVector.fromValidHex(a_pub))
    val b2_pub = PublicKey(ByteVector.fromValidHex(b_pub))

    // Add the partecipants
    manager addPartecipants ("Alice" withInfo (a_pub, "127.0.0.1", 25000),
                            "Bob" withInfo (b_pub, "127.0.0.1", 25001))

    // Creating chunks and entries for transactions

    val fundsa_chunks = manager createPublicChunk(a_pub, chunkType = ChunkType.SIG_P2WPKH)
    val fundsb_chunks = manager createPublicChunk(b_pub, chunkType = ChunkType.SIG_P2WPKH)

    // Public chunk with Alice's signature for transaction T
    val tinit0_chunks = manager.createAuthChunk(a_pub, chunkType = ChunkType.SIG_P2PKH)
    val tinit1_chunks = manager.createAuthChunk(b_pub, chunkType = ChunkType.SIG_P2PKH)

    // Auth chunks for T1 that will contain the signatures from Bob and the Oracle
    val t1_chunks = manager prepareEntry (manager createAuthChunk (b_pub onIndex 1),
                                          manager createAuthChunk (a_pub onIndex 2))

    val twina_chunks = Seq(
      ChunkEntry(chunkType = ChunkType.SECRET_IN, chunkPrivacy= ChunkPrivacy.AUTH, chunkIndex = 0, owner = Option(a2_pub), data = ByteVector.fromValidHex("3030")),
      ChunkEntry(chunkType = ChunkType.SECRET_IN, chunkPrivacy= ChunkPrivacy.AUTH, chunkIndex = 1, owner = Option(b2_pub), data = ByteVector.fromValidHex("3030")),
      ChunkEntry(chunkType = ChunkType.SIG_P2SH, chunkPrivacy= ChunkPrivacy.AUTH, chunkIndex = 2, owner = Option(b2_pub), data = ByteVector.empty),
      ChunkEntry(chunkType = ChunkType.SIG_P2SH, chunkPrivacy= ChunkPrivacy.AUTH, chunkIndex = 3, owner = Option(a2_pub), data = ByteVector.empty))

    val twinb_chunks = Seq(
      ChunkEntry(chunkType = ChunkType.SECRET_IN, chunkPrivacy= ChunkPrivacy.AUTH, chunkIndex = 0, owner = Option(a2_pub), data = ByteVector.fromValidHex("3030")),
      ChunkEntry(chunkType = ChunkType.SECRET_IN, chunkPrivacy= ChunkPrivacy.AUTH, chunkIndex = 1, owner = Option(b2_pub), data = ByteVector.fromValidHex("3030")),
      ChunkEntry(chunkType = ChunkType.SIG_P2SH, chunkPrivacy= ChunkPrivacy.AUTH, chunkIndex = 2, owner = Option(b2_pub), data = ByteVector.empty),
      ChunkEntry(chunkType = ChunkType.SIG_P2SH, chunkPrivacy= ChunkPrivacy.AUTH, chunkIndex = 3, owner = Option(a2_pub), data = ByteVector.empty))

    val tsplit_chunks = manager prepareEntry (manager createAuthChunk (b_pub onIndex 1),
                                              manager createAuthChunk (a_pub onIndex 2))

    // Linking amounts to entries, every bitcoin amount has to be linked to the signatures in order to make the right signatures
    // This is necessary since a recent BIP (Bitcoin Improvement Proposal) makes difficult in segwit transactions to just take the bitcoin amount from previous tx
    val fundsa_entry = manager createEntry ((1 btc), fundsa_chunks)
    val fundsb_entry = manager createEntry ((1 btc), fundsb_chunks)
    //val tinit0_entry = manager createEntry ((0.5 btc) forChunks tinit0_chunks)
    val tinit0_entry = manager createEntry(0.5 btc, tinit0_chunks)
    //val tinit1_entry = manager createEntry ((0.5 btc) forChunks tinit1_chunks)
    val tinit1_entry = manager createEntry(0.5 btc, tinit1_chunks)
    val t1_entry = manager createEntry ((0.99 btc) forChunks t1_chunks)
    val twina_entry = manager createEntry((1 btc) forChunks twina_chunks)
    val twinb_entry = manager createEntry((1 btc) forChunks twinb_chunks)
    val tsplit_entry = manager createEntry((1 btc) forChunks tsplit_chunks)

    // Add the metadata of the transactions, for every raw transaction previously added we save the metadata we just created
    manager.addMeta("Tinit", Seq(tinit0_entry, tinit1_entry))
    manager addMetas ("fundsA" withEntry fundsa_entry,
                      "fundsB" withEntry fundsb_entry,
                      "T1" withEntry t1_entry,
                      "TwinA" withEntry twina_entry,
                      "TwinB" withEntry twinb_entry,
                      "Tsplit" withEntry tsplit_entry)
*/
    // Get the initial state and return it
    manager getState
  }
}
