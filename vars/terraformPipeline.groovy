def call(Map config = [:]) {
    pipeline {
        agent any

        environment {
            AWS_REGION = config.aws_region ?: 'us-east-1'
            TF_VAR_region = AWS_REGION
            TF_VAR_cluster_name = config.cluster_name ?: 'demo-eks-cluster'
            AWS_CREDENTIALS_ID = config.aws_credentials_id ?: 'aws-jenkins-creds'
        }

        stages {
            stage('Checkout Terraform Repo') {
                steps {
                    git branch: config.branch ?: 'main', url: config.repo_url
                }
            }

            stage('Terraform Init') {
                steps {
                    withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', credentialsId: env.AWS_CREDENTIALS_ID]]) {
                        sh """
                            terraform init \
                            -backend-config="bucket=${config.s3_bucket}" \
                            -backend-config="key=eks/terraform.tfstate" \
                            -backend-config="region=$AWS_REGION"
                        """
                    }
                }
            }

            stage('Terraform Plan') {
                steps {
                    withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', credentialsId: env.AWS_CREDENTIALS_ID]]) {
                        sh "terraform plan -var-file=terraform.tfvars"
                    }
                }
            }

            stage('Terraform Apply') {
                steps {
                    input message: 'Approve Terraform Apply?'
                    withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', credentialsId: env.AWS_CREDENTIALS_ID]]) {
                        sh "terraform apply -auto-approve -var-file=terraform.tfvars"
                    }
                }
            }

            stage('Verify EKS Cluster') {
                steps {
                    withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', credentialsId: env.AWS_CREDENTIALS_ID]]) {
                        sh """
                            aws eks --region $AWS_REGION update-kubeconfig --name $TF_VAR_cluster_name
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

