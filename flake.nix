{
  description = "Not Cosmetic Armor - Minecraft 1.20.1 Forge mod development environment";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
  };

  outputs = { self, nixpkgs }:
    let
      systems = [ "x86_64-linux" "aarch64-linux" ];
      forAllSystems = nixpkgs.lib.genAttrs systems;
    in
    {
      devShells = forAllSystems (system:
        let
          pkgs = import nixpkgs {
            inherit system;
            config.allowUnfree = true;
          };

          # Native libraries LWJGL/GLFW need when running the Minecraft client.
          # The shell keeps the host GPU; scripts/run-client.sh can select
          # Mesa's llvmpipe for reproducible headless smoke tests.
          runtimeLibs = with pkgs; [
            alsa-lib
            dbus
            fontconfig
            freetype
            glib
            gtk3
            libGL
            libX11
            libXcursor
            libXext
            libXi
            libXinerama
            libXrandr
            libXrender
            libXtst
            libXxf86vm
            libdrm
            libgbm
            libpulseaudio
            mesa
            nss
            nspr
            udev
          ];

          commonTools = with pkgs; [
            git
            jq
            unzip
            zip
            curl
            xdotool
            xvfb-run
            mesa
            libGL
          ];

          mkDevShell = jdk: pkgs.mkShell {
            packages = commonTools ++ [ jdk ];

            JAVA_HOME = jdk.home;
            LD_LIBRARY_PATH = pkgs.lib.makeLibraryPath runtimeLibs;

            shellHook = ''
              # Forge 1.20.1 needs Java 17. Pin the Gradle Java toolchain to
              # the JDK shipped by this shell so no system JDK is required.
              export GRADLE_OPTS="-Dorg.gradle.java.installations.paths=${jdk.home}"

              echo "Not Cosmetic Armor dev shell (Minecraft 1.20.1, Forge 47.4.28, Java 17)"
              echo "  Java:   $(java -version 2>&1 | head -n 1)"
              echo "  build:  ./gradlew build"
              echo "  jar:    build/libs/"
              echo "  client: ./gradlew runClient   (needs a display, or xvfb-run)"
            '';
          };
        in
        {
          default = mkDevShell pkgs.jdk17;
        });

      formatter = forAllSystems (system:
        let pkgs = import nixpkgs { inherit system; };
        in pkgs.nixpkgs-fmt);
    };
}
