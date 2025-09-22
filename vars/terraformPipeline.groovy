def call(Map config = [:]) {

    // Assign defaults
    def AWS_REGION = config.aws_region ?: 'us-east-1'
    def CLUSTER_NAME = config.cluster_name ?: 'demo-eks-cluster'
    def AWS_CREDENTIALS_ID = config.aws_credentials_id ?: 'aws-jenkins-creds'
    def S3_BUCKET = config.s3_bucket ?: 'your-s3-bucket'
    def BRANCH = config.branch ?: 'main'
    def REPO_URL = config.repo_url ?: 'https://github.com/your-org/terraform-eks-infra.git'

    pipeline {
        agent any

        environment {
            AWS_REGION = "${AWS_REGION}"
            TF_VAR_region = "${AWS_REGION}"
            TF_VAR_cluster_name = "${CLUSTER_NAME}"
            AWS_CREDENTIALS_ID = "${AWS_CREDENTIALS_ID}"
        }

stage('Checkout Terraform Repo') {
    steps {
        echo "🔹 Checking out repo: ${REPO_URL} (branch: ${BRANCH})"
        git branch: BRANCH, 
            url: REPO_URL, 
            credentialsId: 'jenkins-creds'
    }
}

            stage('Terraform Init') {
                steps {
                    echo "🔹 Initializing Terraform in region: ${AWS_REGION}, bucket: ${S3_BUCKET}"
                    withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', credentialsId: AWS_CREDENTIALS_ID]]) {
                        sh """
                            terraform init \
                            -backend-config="bucket=${S3_BUCKET}" \
                            -backend-config="key=eks/terraform.tfstate" \
                            -backend-config="region=${AWS_REGION}"
                        """
                    }
                }
            }

            stage('Terraform Plan') {
                steps {
                    echo "🔹 Running Terraform plan"
                    withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', credentialsId: AWS_CREDENTIALS_ID]]) {
                        sh "terraform plan -var-file=terraform.tfvars"
                    }
                }
            }

            stage('Terraform Apply') {
                steps {
                    echo "🔹 Awaiting approval to apply Terraform changes"
                    input message: 'Approve Terraform Apply?'
                    echo "🔹 Applying Terraform changes"
                    withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', credentialsId: AWS_CREDENTIALS_ID]]) {
                        sh "terraform apply -auto-approve -var-file=terraform.tfvars"
                    }
                }
            }

            stage('Verify EKS Cluster') {
                steps {
                    echo "🔹 Verifying EKS cluster: ${CLUSTER_NAME} in region: ${AWS_REGION}"
                    withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', credentialsId: AWS_CREDENTIALS_ID]]) {
                        sh """
                            aws eks --region ${AWS_REGION} update-kubeconfig --name ${CLUSTER_NAME}
                            kubectl get nodes
                        """
                    }
                }
            }
        }

        post {
            success { echo '✅ Terraform EKS deployment completed successfully!' }
            failure { echo '❌ Deployment failed. Check logs!' }
        }
    }
}
