node {
// -------------------------------------------------------------------------------------------------------
// Parameters

  // sonarqube
  def SONAR_HOST_URL = 'http://192.168.230.147:9000'
  def SONAR_SERVER = 'sonarqube-7.5'
  def SONAR_SCANNER_TOOL = 'sonar-scanner-3.3.0'
  def SONAR_PROJECT_KEY = "${JOB_NAME}"
  def SONAR_SOURCES = 'maven-example/multi3/src'
 
  // artifactory
  def ART_URL = 'http://192.168.230.147:8081/artifactory/'
  def CREDENTIALSID = '61eb9b15-f4bb-4fec-b2ff-ff0648c0bd56'
  def PASSWORDVARIABLE = 'PASSWORD'
  def USERNAMEVARIABLE = 'USERNAME'
  def SOURCEREPO = 'docker-dev-local'
  def TARGETREPO = 'docker-release-local'
  def RESOLVE_SNAPSHOT_REPO = 'maven-snapshots-virtual'
  def RESOLVE_RELEASE_REPO = 'maven-releases-virtual'
  def DEPLOY_SNAPSHOT_REPO = 'maven-snapshots-local'
  def DEPLOY_RELEASE_REPO = 'maven-releases-local'
  def artServer = Artifactory.server('art1')
  def rtMaven = Artifactory.newMavenBuild()
  def buildInfo = Artifactory.newBuildInfo()

  // git
  def GIT_URL_PROJECT = 'https://github.com/gyzong1/project-examples.git'
  def GIT_URL_DOCKER = 'https://github.com/gyzong1/docker-lifecycle-scripts.git'

  // maven
  def MAVEN_TOOL = 'maven'
  def MAVEN_GOALS = 'clean install'
  def POM_PATH = 'maven-example/pom.xml'

  // docker
  def DOCKER_URL = '192.168.230.147:8081'

  // -------------------------------------------------------------------------------------------------------

  // 拉取代码
  stage ('Checkout Code') {
    git GIT_URL_PROJECT
  }
/*
  // Sonar 静态代码扫描
  stage('Sonar') {
    // Sonar scan
    def scannerHome = tool SONAR_SCANNER_TOOL;
    withSonarQubeEnv(SONAR_SERVER) {
      sh "${scannerHome}/bin/sonar-scanner -Dsonar.language=java -Dsonar.projectKey=${JOB_NAME} -Dsonar.sources=${SONAR_SOURCES} -Dsonar.host.url=${SONAR_HOST_URL} -Dsonar.java.binaries=*"
    }
  }

  //添加sonar扫描结果到包上
  stage("Sonar Quality Gate") {  
    sleep 5                     
    //获取sonar扫描结果
    def surl="${SONAR_HOST_URL}/api/measures/component?componentKey=${SONAR_PROJECT_KEY}&metricKeys=alert_status,quality_gate_details,coverage,new_coverage,bugs,new_bugs,reliability_rating,vulnerabilities,new_vulnerabilities,security_rating,sqale_rating,sqale_index,sqale_debt_ratio,new_sqale_debt_ratio,duplicated_lines_density&additionalFields=metrics,periods"
    def response=httpRequest consoleLogResponseBody: true, contentType: 'APPLICATION_JSON', ignoreSslErrors: true, url: surl
    def propssonar = readJSON text: response.content
    if (propssonar.component.measures) {
      propssonar.component.measures.each{ measure ->
        def val
        if (measure.periods){
          val = measure.periods[0].value
        }else {
          val = measure.value
        }
        rtMaven.deployer.addProperty("sonar.quality.${measure.metric}", val)
      }
    }

    //增加sonar扫描结果到artifactory
    rtMaven.deployer.addProperty("qulity.gate.sonarUrl", SONAR_HOST_URL + "/dashboard/index/" + SONAR_PROJECT_KEY)
  }
*/
  // 构建
  stage ('Build Maven') {
    rtMaven.resolver server: artServer, releaseRepo: RESOLVE_RELEASE_REPO, snapshotRepo: RESOLVE_SNAPSHOT_REPO
    rtMaven.deployer server: artServer, releaseRepo: DEPLOY_RELEASE_REPO, snapshotRepo: DEPLOY_SNAPSHOT_REPO
    rtMaven.tool = MAVEN_TOOL
    rtMaven.run pom: POM_PATH, goals: MAVEN_GOALS, buildInfo: buildInfo
  }

  // Checkout Docker code
  stage('Checkout Docker') {
    git GIT_URL_DOCKER
  }

  stage('Resolve') {
    dir('docker-app') {
      withCredentials([usernamePassword(credentialsId: CREDENTIALSID, passwordVariable: PASSWORDVARIABLE, usernameVariable: USERNAMEVARIABLE)]) {
        def uname=env.USERNAME
        def pw=env.PASSWORD
        artServer.username=uname
        artServer.password=pw
        sh 'echo credentials applied'
        def curlstr="curl -u"+uname+':'+pw+" "+"\'"+ART_URL
        def warverstr=curlstr+ "api/search/latestVersion?g=org.jfrog.test&a=multi3&repos=maven-snapshots-local&v=3.7-SNAPSHOT'"
        sh warverstr +' > war/version.txt'
        env.WARVER=readFile('war/version.txt')
        def downloadSpec = """{
          "files": [
            {
              "pattern": "maven-snapshots-local/org/jfrog/test/multi3/3.7-SNAPSHOT/multi3-"""+env.WARVER+""".war",
              "target": "war/webservice.war",
              "flat": "true"
            }
          ]
        }"""
        println(downloadSpec)
        artServer.download(downloadSpec, buildInfo)
      }
    }
  }  

  stage('Build and Deploy') {
    dir('docker-app') {
      withCredentials([usernamePassword(credentialsId: CREDENTIALSID, passwordVariable: PASSWORDVARIABLE, usernameVariable: USERNAMEVARIABLE)]) {
        def uname=env.USERNAME
        def pw=env.PASSWORD
        artServer.username=uname
        artServer.password=pw
        sh 'echo credentials applied'
        def curlstr="curl -u"+uname+':'+pw+" "+"\'"+ART_URL
        sh "sed -i 's#docker_ip#${DOCKER_URL}/docker-virtual#' Dockerfile"
        sh "cat Dockerfile"
        def tagName=DOCKER_URL+'/docker-virtual/docker-app:'+env.BUILD_NUMBER
        buildInfo.env.capture = true
        docker.build(tagName)
        def artDocker= Artifactory.docker server: artServer
        buildInfo = artDocker.push(tagName, 'docker-dev-local')
        artServer.publishBuildInfo(buildInfo)
      }
    }
  }
}

println("===================================================================================")

node {
// -------------------------------------------------------------------------------------------------------
// Parameters

  // sonarqube
  def SONAR_HOST_URL = 'http://192.168.230.147:9000'
  def SONAR_SERVER = 'sonarqube-7.5'
  def SONAR_SCANNER_TOOL = 'sonar-scanner-3.3.0'
  def SONAR_PROJECT_KEY = "${JOB_NAME}"
  def SONAR_SOURCES = 'maven-example/multi3/src'
 
  // artifactory
  def ART_URL = 'http://192.168.230.147:8081/artifactory/'
  def CREDENTIALSID = '61eb9b15-f4bb-4fec-b2ff-ff0648c0bd56'
  def PASSWORDVARIABLE = 'PASSWORD'
  def USERNAMEVARIABLE = 'USERNAME'
  def SOURCEREPO = 'docker-dev-local'
  def TARGETREPO = 'docker-release-local'
  def RESOLVE_SNAPSHOT_REPO = 'maven-snapshots-virtual'
  def RESOLVE_RELEASE_REPO = 'maven-releases-virtual'
  def DEPLOY_SNAPSHOT_REPO = 'maven-snapshots-local'
  def DEPLOY_RELEASE_REPO = 'maven-releases-local'
  def artServer = Artifactory.server('art1')
  def rtMaven = Artifactory.newMavenBuild()
  def buildInfo = Artifactory.newBuildInfo()

  // git
  def GIT_URL_PROJECT = 'https://github.com/gyzong1/project-examples.git'
  def GIT_URL_DOCKER = 'https://github.com/gyzong1/docker-lifecycle-scripts.git'

  // maven
  def MAVEN_TOOL = 'maven'
  def MAVEN_GOALS = 'clean install'
  def POM_PATH = 'maven-example/pom.xml'

  // docker
  def DOCKER_URL = '192.168.230.147:8081'

  // -------------------------------------------------------------------------------------------------------

  // 拉取代码
  stage ('Checkout Code') {
    git GIT_URL_DOCKER
  }

  stage('testing app') {
    dir('docker-app/app-test') {
      withCredentials([usernamePassword(credentialsId: CREDENTIALSID, passwordVariable: PASSWORDVARIABLE, usernameVariable: USERNAMEVARIABLE)]) {
        def uname=env.USERNAME
        def pw=env.PASSWORD
        artServer.username=uname
        artServer.password=pw
        def artDocker= Artifactory.docker server: artServer
        def curlstr="curl -u"+uname+':'+pw+" "+"\'"+ART_URL
        def imageName=DOCKER_URL+'/docker-virtual/docker-app'+':'+env.BUILD_NUMBER
        sh 'docker images | grep docker-app'
        println(imageName)

        docker.image(imageName).withRun('-p 8181:8181') {c ->
          sleep 5
          sh 'curl "http://localhost:8181/swampup/"'
        }

        def testWarResult = curlstr+"api/storage/maven-snapshots-local/org/jfrog/test/multi3/3.7-SNAPSHOT/multi3-"+env.WARVER+".war?properties=qa.testType=junit,selenium;qa.status=approved;' -X PUT"
        println("write testResult back:" + testWarResult);
        sh testWarResult;

        def testDockerResult = curlstr+"api/storage/docker-dev-local/docker-app/"+env.BUILD_NUMBER+"/manifest.json?properties=qa.testType=junit,selenium;qa.status=approved;' -X PUT"
        println("write testResult back:" + testDockerResult);
        sh testDockerResult;
      }
    }
  }

  
  stage('Promotions') {
    dir('docker-app/app-test') {
      withCredentials([usernamePassword(credentialsId: CREDENTIALSID, passwordVariable: PASSWORDVARIABLE, usernameVariable: USERNAMEVARIABLE)]) {
        def uname=env.USERNAME
        def pw=env.PASSWORD
        artServer.username=uname
        artServer.password=pw
        def artDocker= Artifactory.docker server: artServer
        def curlstr="curl -u"+uname+':'+pw+" "+"\'"+ART_URL

        def promotestr=curlstr+"api/build/promote/$JOB_NAME/$BUILD_NUMBER' -X POST -H 'Content-Type: application/vnd.org.jfrog.artifactory.build.PromotionRequest+json' "
        def dockerstr=promotestr + "-T promote-docker.json"
        sh 'pwd'
        sh 'ls -l'
        sh 'cat promote-docker.json'
        println(dockerstr)
        sh dockerstr
        sh 'sed -E "s/@/$BUILD_NUMBER/" retag.json > retag_out.json'
        sh 'cat retag_out.json'
        def retagstr=curlstr+"api/docker/docker-release-local/v2/promote' -X POST -H 'Content-Type: application/json' -T retag_out.json"
        sh retagstr
      }
    }
  }
            /*
        stage('distribute') {
            def distribStr=curlstr+'api/build/distribute/'+env.JOB_NAME+'/'+env.BUILD_NUMBER+"' -X POST -H 'Content-Type: application/json' "
            def dockerDistrib = distribStr+ "-T distributeDocker.json"
            println(dockerDistrib)
            sh dockerDistrib
            def warDistrib = distribStr+"-T distributeWar.json"
            sh warDistrib
        }
        stage('deployment') {
            println("deploy app")
            def aqlSearch = curlstr+"api/search/aql' -X POST -T aql.json";
            println(aqlSearch)
            sh aqlSearch;
              
            def tagList = curlstr+"api/docker/docker-local2/v2/docker-app/tags/list'";
            sh tagList;
              
            println("deploy docker-app image")
            sh "docker stop docker-app"
            sh "docker rm docker-app"
            sh "docker rmi docker-virtual.test.artifactory.com/docker-app:latest"
            sh "docker run -d --name docker-app -p 19999:8181 docker-virtual.test.artifactory.com/docker-app:latest"

            def deployResult = curlstr+"api/storage/docker-local2/docker-app/latest/manifest.json?properties=deploy.server=production;deploy.warVersion=multi3-"+env.WARVER+";deploy.dockerTag="+env.BUILD_NUMBER+"' -X PUT"
            println("write deployResult back:" + deployResult);
            sh deployResult;
        }
        */
         
}