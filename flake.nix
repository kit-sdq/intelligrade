{
  description = "A Nix-flake-based Java development environment";

  inputs.nixpkgs.url = "github:nixos/nixpkgs/nixos-unstable";

  outputs = { self, nixpkgs }:
    let
      javaVersion = 17; # Change this value to update the whole stack
      overlays = [
        (final: prev: rec {
          jdk = prev."jdk${toString javaVersion}";
          gradle = prev.gradle.override { java = jdk; };
          maven = prev.maven.override { inherit jdk; };
        })
      ];
      supportedSystems = [ "x86_64-linux" "aarch64-linux" "x86_64-darwin" "aarch64-darwin" ];
      forEachSupportedSystem = f: nixpkgs.lib.genAttrs supportedSystems (system: f {
        pkgs = import nixpkgs { inherit overlays system; };
      });
    in
    {
      devShells = forEachSupportedSystem ({ pkgs }: {
        default = pkgs.mkShell {
          packages = with pkgs; [
            gradle 
            jdk 
            maven 
            jdt-language-server
          ];
          
          #NIX_LD_LIBRARY_PATH = lib.makeLibraryPath [
          #  pkgs.cc.cc
          #];

          # NIX_LD = builtins.readFile "${pkgs.cc}/nix-support/dynamic-linker";
        };
      });
    };
}
