package Tests_Decentralized

import fr.acinq.bitcoin.Crypto.{PrivateKey, PublicKey}
import fr.acinq.bitcoin._
import scodec.bits.ByteVector
import wf.bitcoin.javabitcoindrpcclient.BitcoinJSONRPCClient

object CreateTransactions {

  def main(args: Array[String]): Unit = {
    createTransaction()
  }

  def createTransaction() : Unit = {
    //regtest addresses of the partecipants
    val alice_addr = "bcrt1qpvya0fx4a3rny37jt8twqe4kt95qwcvagvn7ed"
    val bob_addr = "bcrt1q6hhz23me8gqumprwzdrm4yrcy9yntp7l53nzt6"
    val oracle_addr = "bcrt1qp2g8a0ele0ywv9tc9ywutv58j0prhqznxvltn0"

    //private keys
    val a_priv = PrivateKey.fromBase58("cVbFzgZSpnuKvNT5Z3DofF9dV4Dr1zFQJw9apGZDVaG73ULqM7XS", Base58.Prefix.SecretKeyTestnet)._1
    val b_priv = PrivateKey.fromBase58("cPU3AmQFsBxvrBgTWc1j3pS6T7m4bYWMFQyPnR9Qp3o3UTCBwspZ", Base58.Prefix.SecretKeyTestnet)._1
    val o_priv = PrivateKey.fromBase58("cQAEMfAQwbVDSUDT3snYu9QVfbdBTVMrm36zoArizBkAaPYTtLdH", Base58.Prefix.SecretKeyTestnet)._1

    //public keys
    val a_pub = a_priv.publicKey
    val b_pub = b_priv.publicKey
    val o_pub = o_priv.publicKey

    println(a_pub)
    println(b_pub)
    println(o_pub)

    println(Bech32.encodeWitnessAddress("bcrt", 0, Crypto.hash160(a_pub.value)))

    // funds transaction generated on regtest with sendtoaddress bcrt1qpvya0fx4a3rny37jt8twqe4kt95qwcvagvn7ed
    val fundsA = Transaction.read("020000000001017219856b85b8935f5432ae48e06de75fada29ef17dec1f57ad3eac16889fa5ce0000000000fdffffff0240eef805000000001600140b09d7a4d5ec473247d259d6e066b6596807619d33030d2401000000160014c3cde86d94103703a2f2c382c2ddc52e8bf80973024730440220549e067f53dd51d1d5d578e8cba7aabe8d4efa875a678a345934667087ef3f720220236e1e32016f4d4a0c79d723eeb9d6cd1f9ae92404ec97fa751fa994e8a32705012103fd3c8b7437f9c8b447a3d04aca9ffa04c430c324a49495f13d116395029aa93a00000000")
    val fundsB = Transaction.read("02000000000101578275b2a730b78770ebb34e0ef6d5c88570636f6c1982c8d7fa10056bba475c0000000000fdffffff0233030d24010000001600141ee2afb83070eae3455c47dee2bc22b55115550f40eef80500000000160014d5ee2547793a01cd846e1347ba907821493587df0247304402206ba7565f344d70b0d0e6aa5eca9ed085a1d7ee906891ab14d0ae11e9586b071b02204a23107c1dbc419fe8042d2b5dc4d03e5e244f1fde61e2e63bf9c99b25563e76012103fd3c8b7437f9c8b447a3d04aca9ffa04c430c324a49495f13d116395029aa93a00000000")

  /*val tA = {
      val tmp: Transaction = Transaction(version = 2,
        txIn = TxIn(OutPoint(fundsA.hash, 0), sequence = 0xffffffffL, signatureScript = ByteVector.empty, witness = ScriptWitness.empty) :: Nil,
        txOut = TxOut(1.001 btc, Script.pay2pkh(a_pub)) :: Nil,
        lockTime = 0
      )
      // mind this: the pubkey script used for signing is not the prevout pubscript (which is just a push
      // of the pubkey hash), but the actual script that is evaluated by the script engine, in this case a PAY2PKH script
        val pubKeyScript = Script.pay2pkh(a_pub)
        val sig = Transaction.signInput(tmp, 0, pubKeyScript, SIGHASH_ALL, fundsA.txOut(0).amount, SigVersion.SIGVERSION_WITNESS_V0, a_priv)
        val witness = ScriptWitness(Seq(sig, a_pub.value))
        tmp.updateWitness(0, witness)
    }
    //check that the transaction is spendable
    println(tA)
    Transaction.correctlySpends(tA, Seq(fundsA), ScriptFlags.STANDARD_SCRIPT_VERIFY_FLAGS)

    val tB = {
      val tmp: Transaction = Transaction(version = 2,
        txIn = TxIn(OutPoint(fundsB.hash, 1), sequence = 0xffffffffL, signatureScript = ByteVector.empty, witness = ScriptWitness.empty) :: Nil,
        txOut = TxOut(1.001 btc, Script.pay2pkh(b_pub)) :: Nil,
        lockTime = 0
      )
      // mind this: the pubkey script used for signing is not the prevout pubscript (which is just a push
      // of the pubkey hash), but the actual script that is evaluated by the script engine, in this case a PAY2PKH script
      val pubKeyScript = Script.pay2pkh(b_pub)
      val sig = Transaction.signInput(tmp, 0, pubKeyScript, SIGHASH_ALL, fundsB.txOut(1).amount, SigVersion.SIGVERSION_WITNESS_V0, b_priv)
      val witness = ScriptWitness(Seq(sig, b_pub.value))
      tmp.updateWitness(0, witness)
    }
    //check that the transaction is spendable
    println(tB)
    Transaction.correctlySpends(tB, Seq(fundsB), ScriptFlags.STANDARD_SCRIPT_VERIFY_FLAGS)
*/
    val tinit = {
      val redeemScript = Script.createMultiSigMofN(2, Seq(a_pub, b_pub))
      val tmp: Transaction = Transaction(version = 2,
        txIn = Seq(TxIn(OutPoint(fundsA.hash, 0), sequence = 0xffffffffL, signatureScript = ByteVector.empty, witness = ScriptWitness.empty),
                   TxIn(OutPoint(fundsB.hash, 1), sequence = 0xffffffffL, signatureScript = ByteVector.empty, witness = ScriptWitness.empty)),
        txOut = TxOut(2 btc, Script.pay2wsh(redeemScript)) :: Nil,
        lockTime = 0
      )
      // mind this: the pubkey script used for signing is not the prevout pubscript (which is just a push
      // of the pubkey hash), but the actual script that is evaluated by the script engine, in this case a PAY2PKH script
      val pubKeyScript1 = Script.pay2pkh(a_pub)
      val pubKeyScript2 = Script.pay2pkh(b_pub)
      val sig1 = Transaction.signInput(tmp, 0, pubKeyScript1, SIGHASH_ALL, fundsA.txOut(0).amount, SigVersion.SIGVERSION_WITNESS_V0, a_priv)
      val witness1 = ScriptWitness(Seq(sig1, a_pub.value))
      val sig2 = Transaction.signInput(tmp, 1, pubKeyScript2, SIGHASH_ALL, fundsB.txOut(1).amount, SigVersion.SIGVERSION_WITNESS_V0, b_priv)
      val witness2 = ScriptWitness(Seq(sig2, b_pub.value))
      tmp.updateWitnesses(Seq(witness1, witness2))
    }
    //check that the transaction is spendable
    println(tinit)
    Transaction.correctlySpends(tinit, Seq(fundsA, fundsB), ScriptFlags.STANDARD_SCRIPT_VERIFY_FLAGS)

    val balz_t1 = Transaction.read("02000000010ba86efe0e541f3928036bf4d267996044d8fd09d98c65b4dc11457040f4837600000000e4473044022074199052dcad1d0b01f75252a20ea969c3d65cdb3bc1feeb2cb460b88237157e022057f7633e12aa5fea2512bd8e4620734715b651c4ca61e8b5e1a5a1b39c8f15c701483045022100c02e5dd3997b5b7a93d488027682e3fe049b569527672e9f5be17380b51b9dee02204da98a86d11d8d56196905f4051677dfe9f0d28d599ba0002ee5512f0210e152014c516b6b006c766c766b7c6b5221028c96545ee165f631de2889ac3dd21bdf96efc7b9b92accc36c2107460c72ced72103fd3c8b7437f9c8b447a3d04aca9ffa04c430c324a49495f13d116395029aa93a52aeffffffff01c07fdc0b0000000017a914063e4c1ecae3206bdc95de7e2db9d11b7b2b57c78700000000")
    val t1 = {
      val redeemScript = Script.write(Script.parse(balz_t1.txOut(0).publicKeyScript))
      val tmp: Transaction = Transaction(version = 2,
        txIn = TxIn(OutPoint(tinit.hash, 0), sequence = 0xffffffffL, signatureScript = ByteVector.empty, witness = ScriptWitness.empty) :: Nil,
        txOut = TxOut(1.99 btc, Script.pay2wsh(ByteVector.fromValidHex("02000000000101cc8d82e9dfb4f9fe731e280f62dd4097205ef9bf5bec549003c670ab85b348930000000000ffffffff01c07fdc0b00000000220020c018d868d56e67044072a164cd4ab0be559d84c4b621f29e71a5ab2c8d818b80040047304402204d201a2175c3f76e08460cfa49b8ae7cec959a8f04b8a4022476bf4f4a5e0404022031d85801de7e5c1d15cf58a4817d7a52084cbab93b8dae806070fd56edc078400147304402205e5c6662e3203b7e3090fcfeb648b636a58d4f6b42adba252a5361d9dd0b2ccb02202a9fb9ae0475defd05682f7b840b68e9d1031785df41de788a302a0f67aee8f80147522103fd3c8b7437f9c8b447a3d04aca9ffa04c430c324a49495f13d116395029aa93a21028c96545ee165f631de2889ac3dd21bdf96efc7b9b92accc36c2107460c72ced752ae00000000"))) :: Nil,
        lockTime = 0
      )
      // mind this: the pubkey script used for signing is not the prevout pubscript (which is just a push
      // of the pubkey hash), but the actual script that is evaluated by the script engine, in this case a PAY2PKH script
      val pubKeyScript = Script.write(Script.createMultiSigMofN(2, Seq(a_pub, b_pub)))
      val sig2 = Transaction.signInput(tmp, 0, pubKeyScript, SIGHASH_ALL, tinit.txOut(0).amount, SigVersion.SIGVERSION_WITNESS_V0, a_priv)
      val sig3 = Transaction.signInput(tmp, 0, pubKeyScript, SIGHASH_ALL, tinit.txOut(0).amount, SigVersion.SIGVERSION_WITNESS_V0, b_priv)
      val witness = ScriptWitness(Seq(ByteVector.empty, sig2, sig3, pubKeyScript))
      tmp.updateWitness(0, witness)
    }
    //check that the transaction is spendable
    println(t1)
    //Transaction.correctlySpends(t1, Seq(fundsA, fundsB, tinit), ScriptFlags.STANDARD_SCRIPT_VERIFY_FLAGS)

    val balz_t2 = Transaction.read("02000000015df67a2ef21f124a134e26eb621986a104dd0402c7a17eaadabb377d50148ab800000000fd99034c8030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030304c8030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030314830450221009915e23d1566f6c286bbaf193cbad4d2884717e3718615fdb057c44ccc81508902203b3e9dbeeec2ab4e6246eaa44669fc1dc8503aa12595bfaec93394aaae23f4a0014830450221008567a668cfcbf600de5c1ea4cb46e2d0d575f357a921fd2d87ef312ed5d0400702205bbacf64937d29fdf2cc751f71d89b712ee3ddd380b3ef666a98dc32ff23c1df014d00026b6b6b766b827c756c6c766b7c6b827c7587636c766ba914b472a266d0bd89c13706a4132ccfb16f7c3b9fcb87670068636c766b827c75028000a2670068636c6c766b7c6ba914c51b66bced5e4491001bd702669770dccf44098287670068636c6c766b7c6b827c75028000a267006863006c6c6c766b7c6b7c6c6c6c766b7c6b7c6b7c6b5221028c96545ee165f631de2889ac3dd21bdf96efc7b9b92accc36c2107460c72ced72103fd3c8b7437f9c8b447a3d04aca9ffa04c430c324a49495f13d116395029aa93a52ae6700686351676c766b827c756c6c766b7c6b827c758791636c766ba914b472a266d0bd89c13706a4132ccfb16f7c3b9fcb87670068636c766b827c75028000a2670068636c6c766b7c6ba914c51b66bced5e4491001bd702669770dccf44098287670068636c6c766b7c6b827c75028000a267006863006c6c6c766b7c6b7c6c6c6c766b7c6b7c6b7c6b5221028c96545ee165f631de2889ac3dd21bdf96efc7b9b92accc36c2107460c72ced72103fd3c8b7437f9c8b447a3d04aca9ffa04c430c324a49495f13d116395029aa93a52ae67006868635167006c6c6c766b7c6b7c6c6c6c766b7c6b7c6b7c6b5221028c96545ee165f631de2889ac3dd21bdf96efc7b9b92accc36c2107460c72ced72103fd3c8b7437f9c8b447a3d04aca9ffa04c430c324a49495f13d116395029aa93a52ae68ffffffff01803dcd0b000000001976a9140b09d7a4d5ec473247d259d6e066b6596807619d88ac00000000")
    val t2 = {
      val tmp: Transaction = Transaction(version = 2,
        txIn = TxIn(OutPoint(t1.hash, 0), sequence = 0xffffffffL, signatureScript = ByteVector.empty, witness = ScriptWitness.empty) :: Nil,
        txOut = TxOut(1.98 btc, Script.pay2wpkh(a_pub)) :: Nil,
        lockTime = 0
      )
      // mind this: the pubkey script used for signing is not the prevout pubscript (which is just a push
      // of the pubkey hash), but the actual script that is evaluated by the script engine, in this case a PAY2PKH script
      val pubKeyScript = ByteVector.empty
      val sec1 = ByteVector.fromValidHex("3030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030")
      val sec2 = ByteVector.fromValidHex("3030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303031")
      val sig1 = Transaction.signInput(tmp, 0, pubKeyScript, SIGHASH_ALL, t1.txOut(0).amount, SigVersion.SIGVERSION_WITNESS_V0, a_priv)
      val sig2 = Transaction.signInput(tmp, 0, pubKeyScript, SIGHASH_ALL, t1.txOut(0).amount, SigVersion.SIGVERSION_WITNESS_V0, b_priv)
      val witness = ScriptWitness(Seq(ByteVector.empty, ByteVector.empty, ByteVector.empty, ByteVector.fromValidHex("6b6b6b766b827c756c6c766b7c6b827c7587636c766ba914b472a266d0bd89c13706a4132ccfb16f7c3b9fcb87670068636c766b827c75028000a2670068636c6c766b7c6ba914c51b66bced5e4491001bd702669770dccf44098287670068636c6c766b7c6b827c75028000a267006863006c6c6c766b7c6b7c6c6c6c766b7c6b7c6b7c6b5221028c96545ee165f631de2889ac3dd21bdf96efc7b9b92accc36c2107460c72ced72103fd3c8b7437f9c8b447a3d04aca9ffa04c430c324a49495f13d116395029aa93a52ae6700686351676c766b827c756c6c766b7c6b827c758791636c766ba914b472a266d0bd89c13706a4132ccfb16f7c3b9fcb87670068636c766b827c75028000a2670068636c6c766b7c6ba914c51b66bced5e4491001bd702669770dccf44098287670068636c6c766b7c6b827c75028000a267006863006c6c6c766b7c6b7c6c6c6c766b7c6b7c6b7c6b5221028c96545ee165f631de2889ac3dd21bdf96efc7b9b92accc36c2107460c72ced72103fd3c8b7437f9c8b447a3d04aca9ffa04c430c324a49495f13d116395029aa93a52ae67006868635167006c6c6c766b7c6b7c6c6c6c766b7c6b7c6b7c6b5221028c96545ee165f631de2889ac3dd21bdf96efc7b9b92accc36c2107460c72ced72103fd3c8b7437f9c8b447a3d04aca9ffa04c430c324a49495f13d116395029aa93a52ae68")))
      tmp.updateWitness(0, witness)
    }
    //check that the transaction is spendable
    println(t2)
    //Transaction.correctlySpends(t2, Seq(fundsA, fundsB, tinit, t1), ScriptFlags.STANDARD_SCRIPT_VERIFY_FLAGS)

    val Tinit = Transaction.read("020000000001034ebd9948a3b44846de67815e6cdf32edf2924dad4c03f62276fcced295f997f90100000000ffffffffe46349e58df3e00db58938cd5f9ffcda1fad16afb23f908f2eee9ac93d78a1480100000000ffffffffcf7c7752c144dfc8093918ba01c4d3d8a13107583f5f4502d211a651266dbcc30000000000ffffffff01cff4170000000000220020b151a00b14f0269f58064c3f60fcd3fa95c43edee89da0377fd4accb9bb417fd0247304402207e9b176c5aa6b08e9359a2040ee8ce3ea2c626af37494eddbeeeaf4d1433ad580220421d31ea3bad2384511e8d43f94899273fe447ed1ee6bd6a538be1ae74f2bf8b012102a6d35321c8930c1da17df79edebaf13192ee3e39c9abcea6d8dd9c5f3640e2ab02483045022100bd781d77f5005cddd2e764c9766fd739c787d05b317235cac64cfe4e01661aa6022007259c9d4a031f6441f784dcf9c0ffa3b7d628345ad99f9c667b5ef9d9327451012102a6d35321c8930c1da17df79edebaf13192ee3e39c9abcea6d8dd9c5f3640e2ab02483045022100c2a12def806d26e6bd771e75288b787a62d608f95c36caebb8eb7ca487b0080f0220547d52e0815604e8ee26472f2b0ad41665ae7e27acda805271fbdbbc779ce7d8012102a6d35321c8930c1da17df79edebaf13192ee3e39c9abcea6d8dd9c5f3640e2ab00000000")
    val T1 = Transaction.read("0200000000010167bbbb3eaf9479cc9e93f864bf9190507dc52d84870f3dbfa50ced3b52752c310000000000ffffffff0335d50700000000002200200d793f176345ef0d75f6ffe5e72941f7ae0b914069e23899498ef5573957659735d50700000000002200208d6e3359ebcd5a2a196e142098d8873c4373d30f25c8d94e5c6cdf834054bcdf35d5070000000000220020294c8db7f2158c215795d8d03568f76a27501443ea5f21d2fa243934e2789f8603483045022100902b1bce30d7d439f37ffb6537ec772ba6d575da96b519d7e93f0cab7c7e30850220792d7bc294f08f4744d30a8f6930a6476e0833b09ef697798f1e670c713d7bfe01483045022100f728982e025421963aa509625f0cb80f05ab077f6c83ffc90233821503cb6366022023a26e41a2f28f10ba8a868cf415a3ce34d36958bf127197b8da0d9414c0feaa0152516b6b006c766c766b7c6b522103859a0f601cf485a72ec097fddd798c694b0257f69f0229506f8ea923bc600c5e2103ff41f23b70b1c83b01914eb223d7a97a6c2b24e9a9ef2762bf25ed1c1b83c9c352ae00000000")
    val T5 = Transaction.read("02000000000101535e6fdd13f19af436b68a53ed3c7e5f32af1ccc4a412b678402d5dd88a17ebd0100000000ffffffff010560070000000000220020b151a00b14f0269f58064c3f60fcd3fa95c43edee89da0377fd4accb9bb417fd0480303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303047304402200c865387c69c5483a132e91b644dbebde018d9be2ac5ec1f1738d2460bf7b8e702205ed15a7c7b6eb3e5d0d5fa6dfe1918e1cd46140e520400781d6a1475c91c6a2a0147304402204768bde3300f7d6e1d6f01429a1440c1666dd53b054e0c8c419c36436301f86702202dd5230d7bbea3ad4e800cfd00a1cff5c603c042ff9f73aa1289bcf41803fcc301dcdb6b6b766ba914b472a266d0bd89c13706a4132ccfb16f7c3b9fcb87636c766b827c75028000a267006863006c6c766b7c6c6c766b7c6b7c6b522103859a0f601cf485a72ec097fddd798c694b0257f69f0229506f8ea923bc600c5e2103ff41f23b70b1c83b01914eb223d7a97a6c2b24e9a9ef2762bf25ed1c1b83c9c352ae670068635167006c6c766b7c6c6c766b7c6b7c6b522103859a0f601cf485a72ec097fddd798c694b0257f69f0229506f8ea923bc600c5e2103ff41f23b70b1c83b01914eb223d7a97a6c2b24e9a9ef2762bf25ed1c1b83c9c352ae6800000000")
    val T6 = Transaction.read("0200000000010136388ab1877a467cb8dce7cae4f3110bc99434c60df05e679c360cee82eaf2d50000000000ffffffff01d5ea0600000000001976a914448f9bd84d520fb00adb83f26d8a78ddc5403c8988ac03483045022100cda369bc32273981dcab10cf610a540eef2466e0bd4e5c6a488f5aaaf279767902205e784533b521bb0274b5651c5c6ceabb1912a2469a1593bff8771f1adf445e6f01483045022100a3d483e1793950c8b91fa909627422f458efd45d8cef013061780c76ce49259d022034d9f78fc9e69d21225df210e4153d7053b4df498203717a7a66743615bdc4380152516b6b006c766c766b7c6b522103859a0f601cf485a72ec097fddd798c694b0257f69f0229506f8ea923bc600c5e2103ff41f23b70b1c83b01914eb223d7a97a6c2b24e9a9ef2762bf25ed1c1b83c9c352ae00000000")
    Transaction.correctlySpends(T1, Seq(Tinit), ScriptFlags.STANDARD_SCRIPT_VERIFY_FLAGS)
    Transaction.correctlySpends(T5, Seq(Tinit, T1), ScriptFlags.STANDARD_SCRIPT_VERIFY_FLAGS)
    Transaction.correctlySpends(T6, Seq(Tinit, T1, T5), ScriptFlags.STANDARD_SCRIPT_VERIFY_FLAGS)

  }
}

