#define MAX_STACK_BUF 65536
#define SIG_WAKEUP (__SIGRTMAX - 2)


jfieldID cache_field(JNIEnv* env, const char* holder, const char* field, const char* signature);

void throw_by_name(JNIEnv* env, const char* exception, const char* msg);
void throw_socket_closed(JNIEnv* env);
void throw_io_exception(JNIEnv* env);
