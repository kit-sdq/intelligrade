{
  description = "A Nix-flake-based Java development environment";

  inputs.nixpkgs.url = "github:nixos/nixpkgs/nixos-unstable";

  outputs =
    { self, nixpkgs }:
    let
      javaVersion = 21; # Change this value to update the whole stack
      supportedSystems = [
        "x86_64-linux"
        "aarch64-linux"
        "x86_64-darwin"
        "aarch64-darwin"
      ];
      forEachSupportedSystem = nixpkgs.lib.genAttrs supportedSystems;
    in
    {
      devShells = forEachSupportedSystem (
        system:
        let
          pkgs = nixpkgs.legacyPackages.${system};

          jdk = pkgs."jdk${toString javaVersion}";
          gradle = pkgs.gradle.override { java = jdk; };
          maven = pkgs.maven.override { jdk_headless = jdk; };

          run-ide = pkgs.writeShellScriptBin "run-ide" ''
            ./gradlew runIde --refresh-dependencies
          '';
        in
        {
          default = pkgs.mkShell {
            packages = [
              jdk
              gradle
              maven
              pkgs.jdt-language-server

              run-ide
              (pkgs.writeShellScriptBin "run-ide-non-local" ''
                unset LOCAL_IDEA
                ${run-ide}/bin/run-ide
              '')
            ];

            LOCAL_IDEA = "${pkgs.jetbrains.idea-community-bin}/idea-community";
            LOCAL_JBR = pkgs.jetbrains.jdk;
          };
        }
      );

      formatter = forEachSupportedSystem (system: nixpkgs.legacyPackages.${system}.nixfmt-tree);
    };
}
