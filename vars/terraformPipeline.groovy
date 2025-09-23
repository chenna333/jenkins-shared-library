def call(Map config = [:]) {

    // Defaults
    def AWS_REGION = config.aws_region ?: 'us-east-1'
    def CLUSTER_NAME = config.cluster_name ?: 'demo-eks-cluster'
    def S3_BUCKET = config.s3_bucket ?: 'your-s3-bucket'
    def BRANCH = config.branch ?: 'main'
    def REPO_URL = config.repo_url ?: 'https://github.com/chenna333/intraedge-terraform-eks-infra.git'

    pipeline {
        agent any

        environment {
            AWS_REGION = "${AWS_REGION}"
            TF_VAR_region = "${AWS_REGION}"
            TF_VAR_cluster_name = "${CLUSTER_NAME}"
        }

        stages {

            stage('Checkout Git Repo') {
                steps {
                    echo "🔹 Checking out repo: ${REPO_URL} (branch: ${BRANCH})"
                    git branch: BRANCH, 
                        url: REPO_URL, 
                        credentialsId: 'jenkins-creds' // optional if IAM role works
                    echo "✅ Git checkout completed successfully"
                }
            }

            stage('Terraform Init') {
                steps {
                    echo "🔹 Initializing Terraform in region: ${AWS_REGION}, bucket: ${S3_BUCKET}"
                    sh """
                        terraform init -reconfigure \
                        -backend-config="bucket=${S3_BUCKET}" \
                        -backend-config="key=eks/terraform.tfstate" \
                        -backend-config="region=${AWS_REGION}"
                    """
                }
            }

            stage('Terraform Plan') {
                steps {
                    echo "🔹 Running Terraform plan"
                    sh "terraform plan -var-file=terraform.tfvars"
                }
            }

            stage('Terraform Apply') {
                steps {
                    echo "🔹 Awaiting approval to apply Terraform changes"
                    input message: 'Approve Terraform Apply?'
                    echo "🔹 Applying Terraform changes"
                    sh "terraform apply -auto-approve -var-file=terraform.tfvars"
                }
            }

            stage('Verify EKS Cluster') {
                steps {
                    echo "🔹 Verifying EKS cluster: ${CLUSTER_NAME} in region: ${AWS_REGION}"
                    sh """
                        aws eks --region ${AWS_REGION} update-kubeconfig --name ${CLUSTER_NAME}
                        kubectl get nodes
                    """
                }
            }
        }

        post {
            success { echo '✅ Terraform EKS deployment completed successfully!' }
            failure { echo '❌ Deployment failed. Check logs!' }
        }
    }
}
