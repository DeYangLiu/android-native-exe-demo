#include <stdio.h>
#include <string.h>

int main(int ac, char **av)
{
	printf("ac %d\n", ac);
	int i;
	for (i = 0; i < ac; ++i) {
		printf("%d: %s\n", i, av[i]);
		fflush(stdout);
		
	}
	printf("end of args\n");
	fflush(stdout);
	fprintf(stderr, "stderr: %s\n", av[0]);

	char line[512];

	i = 3;
	while (i--) {
		memset(line, 0, sizeof(line));
		fgets(line, sizeof(line), stdin);
		
		int len = strlen(line);
		if (len > 0) {
			line[len-1] = 0; //
		
			printf("GOT '%s'", line);
			fflush(stdout);
		}
	}
	printf("exit\n");
	
	return 0;
}
