### Creating a GPG Key
##### Important:
This section must be done only once for your Sonatype account.  You must keep the generated secret keys
(`habitap_sonatype_build.gpg` if following the steps below) in a safe place and ***must not upload it to some public
repository (e.g. Github)***.  You must use the same key for your subsequent publications to your account.

##### Steps:
1. Run the command below and choose/enter the following when prompted:
   - Kind: **(1) RSA and RSA (default)**
   - Key size: **4096**
   - Key validity: **(0) key does not expire**
   - User ID:
     - Real name: **Habitap Pte. Ltd.**
     - Email address: **developers@habitap.app**
     - Comment: *&lt;empty>*
   - Password: *&lt;Same as the Sonatype OSSRH account password>*<br>
     <br>

   ```shell
   > gpg --full-generate-key

   gpg: key 4B533CE55C73BFC8 marked as ultimately trusted
   gpg: revocation certificate stored as 'C:/Users/rexmt/AppData/Roaming/gnupg/openpgp-revocs.d\F3DC83EE9DE3CF2F12922B944B533CE55C73BFC8.rev'
   public and secret key created and signed.

   pub   rsa4096 2022-12-07 [SC]
         F3DC83EE9DE3CF2F12922B944B533CE55C73BFC8
   uid                      Habitap Pte. Ltd. <developers@habitap.app>
   sub   rsa4096 2022-12-07 [E]
   ```

2. Export and backup your secret key somewhere safe.  Specify the `--armour` option so that it can be used as an environment variable if needed:

   ```shell
   > gpg --armour --export-secret-keys 5C73BFC8 > C:\Users\rexmt\habitap_sonatype_build.gpg
   ```

3. Upload your public key to a public server so that sonatype can find it:

   ```shell
   > gpg --keyserver keyserver.ubuntu.com --send-keys 5C73BFC8
   ```

*Source: [7. Create a GPG key (Publishing a maven artifact 3/3: step-by-step instructions to MavenCentral publishing)](https://proandroiddev.com/publishing-a-maven-artifact-3-3-step-by-step-instructions-to-mavencentral-publishing-bd661081645d#e14e)*

<br>

### Setting up the Plugin for Publishing
Follow the steps indicated here: https://vanniktech.github.io/gradle-maven-publish-plugin/central/

<br>

### Publishing to Sonatype
1. Run `publish` Gradle task (`swipeRevealLayout` -> `publishing` -> `publish`).
   
2. Login to OSSRH (https://s01.oss.sonatype.org/).
   
3. Locate your staging repository.
   
   <img src="https://central.sonatype.org/images/ossrh-build-promotion-menu.png">

4. Select your repository and click `Close`.
   
   <img src="https://central.sonatype.org/images/ossrh-staging-repo-close.png">

5. Once it's closed, the `Release` button will be enabled.  Click the `Release` button.

For detailed steps: https://central.sonatype.org/publish/release/
