package ch.oeleri.survey

import java.io.{File, FileInputStream}
import java.security.KeyStore

import javax.net.ssl.{KeyManagerFactory, SSLContext, TrustManagerFactory}

import scala.sys.process._

object Ssl {
  def fromPath(path: String, pass: String): SSLContext = {
    val dir = new File(path)
    val keystoreFile = new File(dir, "keystore.pkcs12")
    val pemFile = new File(dir, "fullchain.pem")

    if (!keystoreFile.exists || keystoreFile.lastModified < pemFile.lastModified) {
      s"openssl pkcs12 -export -out $dir/keystore.pkcs12 -in $dir/fullchain.pem -inkey $dir/privkey.pem -passout pass:$pass".!
    }

    val keystore = KeyStore.getInstance("PKCS12")
    keystore.load(new FileInputStream(keystoreFile), pass.toCharArray)

    val keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm)
    keyManagerFactory.init(keystore, pass.toCharArray)

    val sslContext = SSLContext.getInstance("TLSv1.2")
    val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm)
    factory.init(keystore)
    sslContext.init(keyManagerFactory.getKeyManagers, factory.getTrustManagers, null)

    sslContext
  }
}
