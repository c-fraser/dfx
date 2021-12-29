module dfx.util {
  requires kotlin.stdlib;
  requires kotlin.reflect;
  requires kotlinx.coroutines.core.jvm;
  requires org.objectweb.asm;
  requires org.objectweb.asm.commons;
  requires io.netty.buffer;
  exports io.github.cfraser.dfx.util to dfx.rsocket;
}