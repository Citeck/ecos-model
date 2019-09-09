properties([
    disableConcurrentBuilds(),
    [$class: 'RebuildSettings', autoRebuild: false, rebuildDisabled: false],
    [$class: 'ThrottleJobProperty', categories: [], limitOneJobWithMatchingParams: false, maxConcurrentPerNode: 0, maxConcurrentTotal: 0, paramsToUseForLimit: '', throttleEnabled: false, throttleOption: 'project'],
    parameters([
        choice(name: 'SOURCE_SCM', choices: "ecos-model", description: ''),
        booleanParam(name: 'SKIP_TESTS', defaultValue: 'true', description: ''),
        string(name: 'PROFILES', defaultValue: '-Penterprise', description: ''),
        string(name: 'WAR_PROFILES', defaultValue: '', description: ''),
        string(name: 'BRANCH',   defaultValue: 'release-1.0.0',      description: ''),
        booleanParam(name: 'BUILD_ARTIFACTS',defaultValue: 'true',         description: ''),
        booleanParam(name: 'BUILD_WAR',defaultValue: 'false',         description: ''),
        booleanParam(name: 'PUSH_IMAGE',defaultValue: 'true',         description: ''),
        string(name: 'BUILD_STRING', defaultValue: 'mvn clean package build_profiles -DskipTests=skip_tests -Djib.docker.image.tag=branch jib:dockerBuild', description: '')
    ])
])
timestamps {
node {

  def build_str = params.BUILD_STRING
  def current_pom_file = "pom.xml"
  def war_solution_dir = "war-solution/"
  def current_SOURCE_SCM = params.SOURCE_SCM
  def rep_params = [
    pom_file: current_pom_file,
    skip_tests: params.SKIP_TESTS,
    build_profiles: params.PROFILES,
    war_profiles: params.WAR_PROFILES,
    branch: params.BRANCH
  ]
  rep_params.each {rep_param_k,rep_param_v ->
    build_str = build_str.replaceAll("${rep_param_k}","${rep_param_v}")
  }
  def current_workspace = pwd()+"/${current_SOURCE_SCM}/${BRANCH}"
  if (!fileExists("${current_workspace}")) {
    fileOperations([folderCreateOperation("${current_workspace}")])
  }
  ws ("${current_workspace}") {
    checkout([
      $class: 'GitSCM',
      branches: [[name: "${BRANCH}"]],
      doGenerateSubmoduleConfigurations: false,
      extensions: [],
      submoduleCfg: [],
      userRemoteConfigs: [[credentialsId: 'bc074014-bab1-4fb0-b5a4-4cfa9ded5e66',url: "git@bitbucket.org:citeck/${current_SOURCE_SCM}.git"]]
    ])

    if (Boolean.valueOf(BUILD_ARTIFACTS)) {
      stage('Build project artifacts') {
        withMaven(mavenLocalRepo: '/opt/jenkins/.m2/repository', tempBinDir: '') {
          sh "${build_str}"
        }
      }
    }
    if (Boolean.valueOf(PUSH_IMAGE)) {
      docker.withRegistry('https://nexus.citeck.ru', '7d800357-2193-4474-b768-5c27b97a1030') {
        def microserviceImage = "nexus.citeck.ru/"+"${SOURCE_SCM}"+":"+"${BRANCH}"
        def current_microserviceImage = docker.image("${microserviceImage}")
        current_microserviceImage.push()
      }
    }
  }
}
}
