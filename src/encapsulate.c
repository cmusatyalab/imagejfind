#include <stdio.h>
#include <stdint.h>
#include <inttypes.h>

int main(int argc, char **argv)
{
  uint8_t buf[BUFSIZ];
  int64_t curlen;
  int64_t len=0;
  int64_t i;

  if (argc != 2) {
    fprintf(stderr, "Usage: %s <struct_name>\n", argv[0]);
    return 1;
  }

  printf("const static struct {\n");
  printf("  const int64_t len;\n");
  printf("  const uint8_t data[];\n");
  printf("} %s = {\n", argv[1]);
  printf("  .data = {\n");
  while ((curlen=fread(buf, 1, sizeof(buf), stdin))) {
    for (i=0; i<curlen; i++)
      printf("    %" PRId8 ",\n", buf[i]);
    len += curlen;
  }
  printf("  },\n");
  printf("  .len = %" PRId64 "\n", len);
  printf("};\n");
  return 0;
}
